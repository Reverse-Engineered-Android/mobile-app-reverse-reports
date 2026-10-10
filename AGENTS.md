# AGENTS.md

## Identity policy (org-wide, non-negotiable)

No repository under the `Reverse-Engineered-Android` organization may contain
the maintainer's personal GitHub handle, the personal mail address, or any
spelling derived from them. The forbidden values are deliberately not spelled
out in this file, so the policy document itself stays clean; the canonical
check is the case-insensitive regex below, matched against **all Git data**:

```
v[i1]nc[e3]nt[-._]?163
```

Apply it to every part of the repository, not just the working tree:

- commit author and committer name/email;
- commit, tag and annotated-tag messages;
- file contents, file names, symlink targets, and `.mailmap`;
- branches, tags, notes, stash entries and any other ref.

The only permitted identity is:

```
Reverse-Engineered-Android <reverse-engineered-android@users.noreply.github.com>
```

Rules for any commit created in, or any history rewritten for, an org repo:

1. Author and committer must be the org identity above. Never let a personal
   `user.name`/`user.email` be used for a commit pushed into this organization,
   and never leave one in a clone that pushes here.
2. Before pushing, run the scan below and require zero hits. `git log` alone is
   not enough: it misses blob contents and refs other than the checked-out one.
3. If a forbidden identity is already in history, rewrite it (for example
   `git filter-repo --mailmap <file>`), force-push the branch, then confirm the
   remote tip is clean. GitHub may keep the old unreferenced commits reachable
   by raw SHA until Support purges them, so report that separately.

```bash
PAT='v[i1]nc[e3]nt[-._]?163'

# identities and commit/tag messages
git log --all --format='%an|%ae|%cn|%ce|%s|%b' | grep -iE "$PAT"

# blob contents across every reachable commit (0 hits = clean)
git grep -l -iE "$PAT" $(git rev-list --all)
```

Note: vendor strings that merely contain the letters `vincent` are not identity
leaks and must be preserved as evidence. For example Xiaohongshu's own
`fe.xiaohongshu.com/apps/vincent/ditto/...` privacy-page path (which redirects
to `.../dittossr/vincent/...`) is a first-party endpoint, not a username.
