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
void swapIfGreater_Array_Int_Int(int* arr,int i,int j) {
int var4, var7, var9, tmp, var10, var13, var14;
bool var1;
var4 = 0;
var4=arr[i];
var7 = 0;
var7=arr[j];
var1=var4>var7;
if (!var1)goto lbl1;
var9 = 0;
var9=arr[i];
tmp = var9;
var10 = i;
var13 = 0;
var13=arr[j];
arr[var10]=var13;
var14 = j;
arr[var14]=tmp;
lbl1:
return;
}
void bubblePass_Array_Int(int* arr,int size) {
int var18, i, var20, $right_borderi, var25, next, var30;
bool var16, var21;
var18 = 1;
var16=size<=var18;
if (!var16)goto lbl2;
return;
lbl2:
i = 0;
var20 = 1;
$right_borderi=size-var20;
lbl3:
var21=i<=$right_borderi;
if (!var21)goto lbl4;
var25 = 1;
next=i+var25;
swapIfGreater_Array_Int_Int(arr,i,next);
var30 = 1;
i=i+var30;
goto lbl3;
lbl4:
return;
}
int main(int size, char** arg) {
int arr[5];
int var31, var32, var33, var34, var35, var36, var37, var38, var39, var40, var42, var44, var45, var47, var49, var50, var52, var56, var60;
char head[1024], tail[1024], part[1024], var54[1024], line[1024];
var31 = 0;
var32 = 3;
arr[var31]=var32;
var33 = 1;
var34 = 1;
arr[var33]=var34;
var35 = 2;
var36 = 4;
arr[var35]=var36;
var37 = 3;
var38 = 2;
arr[var37]=var38;
var39 = 4;
var40 = 0;
arr[var39]=var40;
var42 = 4;
bubblePass_Array_Int(arr,var42);
strncpy(head, "", sizeof(head));
var44 = 0;
var45 = 0;
var45=arr[var44];
var47 = 1024;
intToString(var45,head,var47);
strncpy(tail, "", sizeof(tail));
var49 = 4;
var50 = 0;
var50=arr[var49];
var52 = 1024;
intToString(var50,tail,var52);
strncpy(part, "", sizeof(part));
strncpy(var54, ",", sizeof(var54));
var56 = 1024;
stringConcat(head,var54,part,var56);
strncpy(line, "", sizeof(line));
var60 = 1024;
stringConcat(part,tail,line,var60);
println(line);
readInt();
return;
}
