# HOTL Workflow: Mixed Folder Reader Ordering

1. Inspect the live mixed-folder, reader, cache, and recursive ordering paths before changing behavior.
2. Introduce an explicit reader session snapshot so a reader launch owns its image list and initial target.
3. Add the mixed-folder "Browse all" entry point and keep folder/media interactions independent.
4. Add the persisted recursive image arrangement setting and apply it only to recursive image lists.
5. Cover ordering and reader-session isolation with JVM tests.
6. Build, install, and verify the requested mixed-folder scenarios on the specified ADB device; retain evidence outside source paths.
