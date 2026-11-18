# Baidu Map SDK

Keep these three vendor modules together:

| File | Maven coordinate | SHA-256 |
| --- | --- | --- |
| BaiduMapSDK_Map-7.6.7.aar | com.baidu.lbsyun:BaiduMapSDK_Map:7.6.7 | 48656ac926d29efd588de87ba46e0885cf121e92370812e658ecf3a6271484a3 |
| base-7.6.7.aar | com.baidu.lbsyun:base:7.6.7 | c0686af2ee5fe0b2e73b9824c3f4d8d26e73755abaf949829d876c93d7cd6752 |
| common-1.0.42.aar | com.baidu.lbsyun:common:1.0.42 | 4355d4627c0f9489984383605fa382cff68b33e412849766102f65f1e45911dd |

Downloaded from Maven Central under `com/baidu/lbsyun/`. All three files were
checked against the repository's published SHA-1 checksums. The map POM selects
base 7.6.7; the base POM selects common 1.0.42. Common is now an AAR, including
its vendor rules and embedded JAR, rather than a standalone JAR.

The four arm64 libraries in map/base 7.6.7 have 0x4000-aligned ELF LOAD segments.
Version 7.6.4 had four 0x1000-aligned libraries. Version 7.6.5.1 was also inspected
and rejected because its libtiny_magic.so still had 0x1000 alignment.

After any SDK update, run `tools/check-apk-native-alignment.ps1` against the
assembled APK. Checking APK ZIP alignment alone does not verify ELF LOAD
alignment. Hardware map rendering and authentication still require device tests.
