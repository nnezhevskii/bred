#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <time.h>

#ifdef _WIN32
#include <sys/timeb.h>
#else
#include <sys/time.h>
#endif

#define STR_BUF_SIZE 1024

// 1. readString(dest, max_size)
void readString(char* dest, int max_size) {
    if (fgets(dest, max_size, stdin) == NULL) {
        dest[0] = '\0';
    }
    size_t len = strlen(dest);
    if (len > 0 && dest[len - 1] == '\n') {
        dest[len - 1] = '\0';
    }
}

// 2. println(String) -> Unit (void)
void println(const char* str) {
    if (str) printf("%s\n", str);
    else printf("\n");
}

// 3. stringToInt(String) -> Int
int stringToInt(const char* str) {
    if (!str) return 0;
    return atoi(str);
}

// 4. intToString(Int, dest, max_size)
void intToString(int val, char* dest, int max_size) {
#if defined(_MSC_VER)
    // MSVC требует безопасный sprintf_s
    sprintf_s(dest, max_size, "%d", val);
#else
    snprintf(dest, max_size, "%d", val);
#endif
}

// 5. doubleToString(Double, dest, max_size)
void doubleToString(double val, char* dest, int max_size) {
#if defined(_MSC_VER)
    sprintf_s(dest, max_size, "%g", val);
#else
    snprintf(dest, max_size, "%g", val);
#endif
}

// 6. stringToDouble(String) -> Double
double stringToDouble(const char* str) {
    if (!str) return 0.0;
    return atof(str);
}

// 7. intToDouble(Int) -> Double
double intToDouble(int val) {
    return (double)val;
}

// 8. readInt() -> Int
int readInt() {
    int val = 0;
#if defined(_MSC_VER)
    if (scanf_s("%d", &val) != 1) {
#else
    if (scanf("%d", &val) != 1) {
#endif
        while (getchar() != '\n');
    }
    return val;
}

// 9. readDouble() -> Double
double readDouble() {
    double val = 0.0;
#if defined(_MSC_VER)
    if (scanf_s("%lf", &val) != 1) {
#else
    if (scanf("%lf", &val) != 1) {
#endif
        while (getchar() != '\n');
    }
    return val;
}

// 10. readBoolean() -> Bool
bool readBoolean() {
    char buffer[16];
#if defined(_MSC_VER)
    if (scanf_s("%15s", buffer, (unsigned)sizeof(buffer)) != 1) return false;
#else
    if (scanf("%15s", buffer) != 1) return false;
#endif
    if (strcmp(buffer, "true") == 0 || strcmp(buffer, "1") == 0) return true;
    return false;
}

// 11. doubleToInt(Double) -> Int
int doubleToInt(double val) {
    return (int)val;
}

// 12. booleanToString(Bool, dest, max_size)
void booleanToString(bool val, char* dest, int max_size) {
    const char* src = val ? "true" : "false";
#if defined(_MSC_VER)
    strcpy_s(dest, max_size, src);
#else
    strncpy(dest, src, max_size);
    dest[max_size - 1] = '\0';
#endif
}

// 13. stringLength(String) -> Int
int stringLength(const char* str) {
    if (!str) return 0;
    return (int)strlen(str);
}

// 14. stringConcat(String1, String2, dest, max_size)
void stringConcat(const char* str1, const char* str2, char* dest, int max_size) {
    dest[0] = '\0';
#if defined(_MSC_VER)
    if (str1) strcpy_s(dest, max_size, str1);
    if (str2) strcat_s(dest, max_size, str2);
#else
    if (str1) {
        strncpy(dest, str1, max_size);
        dest[max_size - 1] = '\0';
    }
    if (str2) {
        int current_len = strlen(dest);
        strncat(dest, str2, max_size - current_len - 1);
    }
#endif
}

// 15. stringEquals(String, String) -> Bool
bool stringEquals(const char* str1, const char* str2) {
    if (str1 == str2) return true;
    if (!str1 || !str2) return false;
    return strcmp(str1, str2) == 0;
}

// 16. substring(String, start, end, dest, max_size)
void substring(const char* str, int start, int end, char* dest, int max_size) {
    dest[0] = '\0';
    if (!str) return;

    int len = (int)strlen(str);
    if (start < 0) start = 0;
    if (end > len) end = len;
    if (start > end) start = end;

    int subLen = end - start;
    if (subLen >= max_size) subLen = max_size - 1;

    if (subLen > 0) {
        strncpy(dest, str + start, subLen);
        dest[subLen] = '\0';
    }
}

// 17. currentTimeMillis() -> Int
int currentTimeMillis() {
#ifdef _WIN32
    struct _timeb timebuf;
    _ftime_s(&timebuf);
    return (int)(timebuf.time * 1000 + timebuf.millitm);
#else
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (int)(tv.tv_sec * 1000 + tv.tv_usec / 1000);
#endif
}

// 18. random(min, max) -> Int
int getRandomInt(int min, int max) {
    return rand() % (max - min + 1) + min;
}