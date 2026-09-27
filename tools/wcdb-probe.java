import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class WcdbProbe {
    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new IllegalStateException("missing method: " + type.getName() + "." + name + "/" + parameterCount);
    }

    private static List<String> query(Object database, String sql) throws Exception {
        Method rawQuery = findMethod(database.getClass(), "rawQuery", 3);
        Object cursor = rawQuery.invoke(database, sql, null, null);
        try {
            Method moveToNext = findMethod(cursor.getClass(), "moveToNext", 0);
            Method getColumnCount = findMethod(cursor.getClass(), "getColumnCount", 0);
            Method getString = findMethod(cursor.getClass(), "getString", 1);
            List<String> rows = new ArrayList<String>();
            while ((Boolean) moveToNext.invoke(cursor)) {
                int columns = ((Integer) getColumnCount.invoke(cursor)).intValue();
                StringBuilder row = new StringBuilder();
                for (int index = 0; index < columns; index++) {
                    if (index > 0) {
                        row.append('\t');
                    }
                    Object value = getString.invoke(cursor, index);
                    row.append(value == null ? "" : value.toString());
                }
                rows.add(row.toString());
            }
            return rows;
        } finally {
            Method close = findMethod(cursor.getClass(), "close", 0);
            close.invoke(cursor);
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String sqlIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static boolean isShadowTable(String value) {
        return value.endsWith("_config")
                || value.endsWith("_content")
                || value.endsWith("_data")
                || value.endsWith("_docsize")
                || value.endsWith("_idx");
    }

    private static Object createCipherSpec() throws Exception {
        if (!"1".equals(System.getenv("WDB_SQLCIPHER_COMPAT"))) {
            return null;
        }
        Class<?> specClass = Class.forName("com.tencent.wcdb.database.SQLiteCipherSpec");
        Object spec = specClass.getConstructor().newInstance();
        Method setPageSize = findMethod(specClass, "setPageSize", 1);
        setPageSize.invoke(spec, 1024);
        Method setSQLCipherVersion = findMethod(specClass, "setSQLCipherVersion", 1);
        setSQLCipherVersion.invoke(spec, 1);
        return spec;
    }

    private static void initializeCso() throws Exception {
        String libraryDirectory = System.getenv("WDB_LIB_DIR");
        System.load(new File(libraryDirectory, "libcso.so").getPath());
        System.load(new File(libraryDirectory, "libWCDB.so").getPath());
        Class<?> csoClass = Class.forName("com.tencent.cso.CsoLoader");
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        Object loader = allocateInstance.invoke(unsafe, csoClass);
        Method initialize = null;
        for (Method method : csoClass.getDeclaredMethods()) {
            if (method.getName().equals("nativeInitialize") && method.getParameterCount() == 9) {
                initialize = method;
                break;
            }
        }
        if (initialize == null) {
            throw new IllegalStateException("missing CsoLoader.nativeInitialize");
        }
        initialize.setAccessible(true);
        String applicationPath = System.getenv("WDB_APP_PATH");
        String basePath = System.getenv("WDB_BASE_PATH");
        initialize.invoke(loader, applicationPath, basePath, "3180", null,
                new String[0], false, new String[] { libraryDirectory },
                new String[] { "/system/lib64", "/apex/com.android.tethering/lib64", "/apex/com.android.art/lib64" }, false);
        Method preload = null;
        for (Method method : csoClass.getDeclaredMethods()) {
            if (method.getName().equals("preloadAllInternal") && method.getParameterCount() == 0) {
                preload = method;
                break;
            }
        }
        if (preload != null) {
            preload.setAccessible(true);
            preload.invoke(loader);
        }
    }

    private static String probe(String databasePath, byte[] key) {
        try {
            Class<?> databaseClass = Class.forName("com.tencent.wcdb.database.SQLiteDatabase");
            Method open = null;
            for (Method method : databaseClass.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals("openDatabase")
                        && parameters.length == 7
                        && parameters[0] == String.class
                        && parameters[1] == byte[].class) {
                    open = method;
                    break;
                }
            }
            if (open == null) {
                throw new IllegalStateException("missing WCDB read-only openDatabase");
            }
            Object cipherSpec = createCipherSpec();
            Object database = open.invoke(null, databasePath, key, cipherSpec, null, 1, null, 0);
            try {
                List<String> master = query(database, "SELECT type || ':' || name FROM sqlite_master ORDER BY type, name");
                String integritySql = System.getenv("WDB_SKIP_INTEGRITY") == null
                        ? "PRAGMA integrity_check"
                        : "SELECT 'skipped'";
                List<String> integrity = query(database, integritySql);
                List<String> pageSize = query(database, "PRAGMA page_size");
                List<String> pageCount = query(database, "PRAGMA page_count");
                List<String> freelistCount = query(database, "PRAGMA freelist_count");
                List<String> encoding = query(database, "PRAGMA encoding");
                String countFilter = System.getenv("WDB_COUNT_TABLES");
                List<String> visibleTables = new ArrayList<String>();
                List<String> ftsTables = new ArrayList<String>();
                for (String entry : master) {
                    if (!entry.startsWith("table:")) {
                        continue;
                    }
                    String tableName = entry.substring(6);
                    boolean selected = countFilter == null
                            ? !isShadowTable(tableName)
                            : ("," + countFilter + ",").contains("," + tableName + ",");
                    if (!selected) {
                        continue;
                    }
                    visibleTables.add(tableName);
                    if (tableName.startsWith("FTS5Index") && System.getenv("WDB_FTS_VOCAB") != null) {
                        ftsTables.add(tableName);
                    }
                }
                StringBuilder tables = new StringBuilder();
                for (int index = 0; index < master.size(); index++) {
                    if (index > 0) {
                        tables.append(',');
                    }
                    tables.append('"').append(jsonEscape(master.get(index))).append('"');
                }
                StringBuilder tableCounts = new StringBuilder();
                for (int index = 0; index < visibleTables.size(); index++) {
                    if (index > 0) {
                        tableCounts.append(',');
                    }
                    String tableName = visibleTables.get(index);
                    String countValue = "-1";
                    try {
                        List<String> count = query(database, "SELECT count(*) FROM " + sqlIdentifier(tableName));
                        if (!count.isEmpty()) {
                            countValue = count.get(0);
                        }
                    } catch (Throwable ignored) {
                    }
                    tableCounts.append('"').append(jsonEscape(tableName)).append("\":").append(countValue);
                }
                StringBuilder ftsAggregates = new StringBuilder();
                Method execSQL = findMethod(database.getClass(), "execSQL", 1);
                for (int index = 0; index < ftsTables.size(); index++) {
                    if (index > 0) {
                        ftsAggregates.append(',');
                    }
                    String tableName = ftsTables.get(index);
                    String vocabName = "temp.wcdb_vocab_" + index;
                    String aggregateJson = "\"" + jsonEscape(tableName)
                            + "\":{\"terms\":-1,\"docs\":-1,\"occurrences\":-1,\"max_doc\":-1,\"max_occurrences\":-1}";
                    try {
                        execSQL.invoke(database, "CREATE VIRTUAL TABLE " + vocabName
                                + " USING fts5vocab(" + sqlIdentifier(tableName) + ", 'row')");
                        List<String> aggregate = query(database, "SELECT count(*), coalesce(sum(doc), 0), "
                                + "coalesce(sum(cnt), 0), coalesce(max(doc), 0), coalesce(max(cnt), 0) FROM " + vocabName);
                        String[] values = aggregate.isEmpty() || aggregate.get(0).length() == 0
                                ? new String[] {"-1", "-1", "-1", "-1", "-1"}
                                : aggregate.get(0).split("\t", -1);
                        StringBuilder aggregateBuilder = new StringBuilder();
                        aggregateBuilder.append('"').append(jsonEscape(tableName)).append("\":{\"terms\":")
                                .append(values[0]).append(",\"docs\":").append(values[1])
                                .append(",\"occurrences\":").append(values[2])
                                .append(",\"max_doc\":").append(values[3])
                                .append(",\"max_occurrences\":").append(values[4]).append('}');
                        aggregateJson = aggregateBuilder.toString();
                    } catch (Throwable ignored) {
                    } finally {
                        try {
                            execSQL.invoke(database, "DROP TABLE IF EXISTS " + vocabName);
                        } catch (Throwable ignored) {
                        }
                    }
                    ftsAggregates.append(aggregateJson);
                }
                return "{\"opened\":true,\"table_count\":" + master.size()
                        + ",\"integrity\":\"" + jsonEscape(integrity.isEmpty() ? "" : integrity.get(0))
                        + "\",\"page_size\":\"" + jsonEscape(pageSize.isEmpty() ? "" : pageSize.get(0))
                        + "\",\"page_count\":\"" + jsonEscape(pageCount.isEmpty() ? "" : pageCount.get(0))
                        + "\",\"freelist_count\":\"" + jsonEscape(freelistCount.isEmpty() ? "" : freelistCount.get(0))
                        + "\",\"encoding\":\"" + jsonEscape(encoding.isEmpty() ? "" : encoding.get(0))
                        + "\",\"table_counts\":{" + tableCounts + "}"
                        + ",\"fts_aggregates\":{" + ftsAggregates + "}"
                        + ",\"tables\":[" + tables + "]}";
            } finally {
                Method close = findMethod(database.getClass(), "close", 0);
                close.invoke(database);
            }
        } catch (Throwable error) {
            Throwable cause = error;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            String message = cause.getMessage();
            if (message == null) {
                message = "";
            }
            if (message.length() > 240) {
                message = message.substring(0, 240);
            }
            return "{\"opened\":false,\"error\":\"" + jsonEscape(cause.getClass().getName())
                    + "\",\"message\":\"" + jsonEscape(message) + "\"}";
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: WcdbProbe <database> <candidate-file>");
        }
        initializeCso();
        File candidateFile = new File(args[1]);
        BufferedReader reader = new BufferedReader(new FileReader(candidateFile));
        try {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                int separator = line.indexOf('\t');
                String keyText = separator < 0 ? line : line.substring(separator + 1);
                byte[] key = keyText.equals("@null") ? null : keyText.getBytes(StandardCharsets.UTF_8);
                String result = probe(args[0], key);
                System.out.println("{\"candidate_index\":" + index + "," + result.substring(1));
                System.out.flush();
                if (result.contains("\"opened\":true")) {
                    break;
                }
                index++;
            }
        } finally {
            reader.close();
        }
    }
}
