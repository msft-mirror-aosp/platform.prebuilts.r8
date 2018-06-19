java_import(
    name = "d8-master",
    jars = ["d8-master.jar"],
    visibility = ["//tools/base/build-system/builder:__pkg__"],
)

java_binary(
    name = "d8",
    main_class = "com.android.tools.r8.D8",
    visibility = ["//visibility:public"],
    runtime_deps = [":d8-master"],
)
