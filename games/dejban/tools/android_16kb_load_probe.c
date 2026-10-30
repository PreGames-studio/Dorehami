/* Diagnostic loader for a Godot shared library; this is not a game APK. */
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc != 3) {
        fprintf(stderr, "usage: %s LIBRARY EXPECTED_PAGE_SIZE\n", argv[0]);
        return 2;
    }
    char *end = NULL;
    long expected_page_size = strtol(argv[2], &end, 10);
    if (*argv[2] == '\0' || *end != '\0' || (expected_page_size != 4096 && expected_page_size != 16384)) {
        fprintf(stderr, "Expected page size must be 4096 or 16384.\n");
        return 2;
    }
    long page_size = sysconf(_SC_PAGESIZE);
    printf("Runtime page size: %ld\n", page_size);
    if (page_size != expected_page_size) {
        fprintf(stderr, "Unexpected emulator page size.\n");
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
