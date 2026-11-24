/* Host-only adapter for the single-process ReXGlue code generator in PRoot.
 * PRoot's /dev does not provide /dev/shm. Analysis needs an anonymous shared
 * backing object, not a named object shared across independently started apps.
 * This is NOT part of the Android APK or a general POSIX shm replacement.
 */
extern long syscall(long number, ...);
int shm_open(const char* name, int flags, unsigned int mode) {
    (void)flags; (void)mode;
    return (int)syscall(279L, name, 1U); /* AArch64 memfd_create, MFD_CLOEXEC */
}
int shm_unlink(const char* name) { (void)name; return 0; }
