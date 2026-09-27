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
            Object database = open.invoke(null, databasePath, key, null, null, 1, null, 0);
            try {
                List<String> master = query(database, "SELECT type || ':' || name FROM sqlite_master ORDER BY type, name");
                List<String> integrity = query(database, "PRAGMA integrity_check");
                List<String> pageSize = query(database, "PRAGMA page_size");
                List<String> pageCount = query(database, "PRAGMA page_count");
                List<String> freelistCount = query(database, "PRAGMA freelist_count");
                List<String> encoding = query(database, "PRAGMA encoding");
                StringBuilder tables = new StringBuilder();
                for (int index = 0; index < master.size(); index++) {
                    if (index > 0) {
                        tables.append(',');
                    }
                    tables.append('"').append(jsonEscape(master.get(index))).append('"');
                }
                return "{\"opened\":true,\"table_count\":" + master.size()
                        + ",\"integrity\":\"" + jsonEscape(integrity.isEmpty() ? "" : integrity.get(0))
                        + "\",\"page_size\":\"" + jsonEscape(pageSize.isEmpty() ? "" : pageSize.get(0))
                        + "\",\"page_count\":\"" + jsonEscape(pageCount.isEmpty() ? "" : pageCount.get(0))
                        + "\",\"freelist_count\":\"" + jsonEscape(freelistCount.isEmpty() ? "" : freelistCount.get(0))
                        + "\",\"encoding\":\"" + jsonEscape(encoding.isEmpty() ? "" : encoding.get(0))
                        + "\",\"tables\":[" + tables + "]}";
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
