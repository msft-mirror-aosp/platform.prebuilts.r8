# managed by go/iml_to_build
java_import(
    name = "r8-master",
    jars = ["r8-master.jar"],
    visibility = [
        "//tools/base/build-system/builder:__pkg__",
        "//tools/base/deploy/deployer:__pkg__",
        "//tools/base/deploy/test:__pkg__",
    ],
)

java_binary(
    name = "d8",
    main_class = "com.android.tools.r8.D8",
    visibility = ["//visibility:public"],
    runtime_deps = [":r8-master"],
)
