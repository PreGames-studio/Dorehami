/* Diagnostic loader for a Godot shared library; this is not a game APK. */
#include <dlfcn.h>
#include <stdio.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s LIBRARY\n", argv[0]);
        return 2;
    }
    long page_size = sysconf(_SC_PAGESIZE);
    printf("Runtime page size: %ld\n", page_size);
    if (page_size != 16384) {
        fprintf(stderr, "Expected a 16 KiB emulator.\n");
        return 3;
    }
    void *handle = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        fprintf(stderr, "dlopen failed: %s\n", dlerror());
        return 1;
    }
    printf("Godot native library loaded successfully.\n");
    dlclose(handle);
    return 0;
}
