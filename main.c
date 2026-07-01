#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <time.h>
#include <string.h>

#ifdef _WIN32
#include <sys/timeb.h>
#else
#include <sys/time.h>
#endif

char *STRING_POOL = NULL;
size_t string_pool_offset = 0;
const size_t POOL_SIZE = 1024 * 1024 * 16; // 16 МБ

typedef struct {
    const char* data;
    size_t length;
} String;

void init_runtime() {
    STRING_POOL = (char*)malloc(POOL_SIZE);
    if (!STRING_POOL) {
        fprintf(stderr, "String pool overflow\n");
        exit(1);
    }
}

String create_string(const char *origin, size_t len) {
    size_t needed = len + 1;

    if (string_pool_offset + needed > POOL_SIZE) {
        fprintf(stderr, "String pool overflow!\n");
        exit(1);
    }

    char* result = STRING_POOL + string_pool_offset;
    string_pool_offset += needed;

    memcpy(result, origin, len);
    result[len] = '\0';

    String s = { .data = result, .length = len };
    return s;
}

String concat(String s1, String s2) {
size_t new_len = s1.length + s2.length;
    size_t needed = new_len + 1;

    if (string_pool_offset + needed > POOL_SIZE) {
        fprintf(stderr, "run out of poll\n");
        exit(1);
    }

    char* result = STRING_POOL + string_pool_offset;
    string_pool_offset += needed;

    memcpy(result, s1.data, s1.length);
    memcpy(result + s1.length, s2.data, s2.length);

    result[new_len] = '\0';

    String s = { .data = result, .length = new_len };
    return s;
}

String readString() {
    #define READ_STRING_MAX_SIZE 1024
    char buff[READ_STRING_MAX_SIZE];

    if (fgets(buff, READ_STRING_MAX_SIZE, stdin) == NULL) {
        String empty = { .data = "", .length = 0 };
        return empty;
    }
    size_t len = strlen(buff);
    if (len > 0 && buff[len - 1] == '\n') {
        buff[len - 1] = '\0';
        len--;
    }

    if (len == 0) {
        String empty = { .data = "", .length = 0 };
        return empty;
    }
    #undef READ_STRING_MAX_SIZE
    return create_string(buff, len);
}

void println(String str) {
    if (str.data) printf("%s\n", str.data);
    else printf("\n");
}

static size_t get_int_string_len(int val) {
    if (val == 0) return 1;

    size_t len = 0;
    long long n = val;

    if (n < 0) {
        len++; // Место под минус
        n = -n;
    }

    while (n > 0) {
        len++;
        n /= 10;
    }
    return len;
}

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

String intToString(int val) {
    size_t len = get_int_string_len(val);
    size_t needed = len + 1;

    if (string_pool_offset + needed > POOL_SIZE) {
        fprintf(stderr, "run out of poll!\n");
        exit(1);
    }

    char* result = STRING_POOL + string_pool_offset;
    string_pool_offset += needed;

    result[len] = '\0';

    long long n = val;
    int is_negative = 0;

    if (n < 0) {
        is_negative = 1;
        n = -n;
    }

    size_t i = len;
    if (n == 0) {
        result[0] = '0';
    } else {
        while (n > 0) {
            i--;
            result[i] = '0' + (n % 10);
            n /= 10;
        }
        if (is_negative) {
            result[0] = '-';
        }
    }

    String s = { .data = result, .length = len };
    return s;
}

// 18. random(min, max) -> Int
int random(int min, int max) {
    return rand() % (max - min + 1) + min;
}
int main(int size, char** arg) {
init_runtime();
int arr[1024];
int arrSize, i, _right_borderi, var4, var6, var7, var5, var9, var10;
bool var1;
arrSize = readInt();
i = 0;
_right_borderi = arrSize;
lbl1:
var1=i<_right_borderi;
if (!var1)goto lbl2;
var4 = i;
var6 = 0;
var7 = 50;
var5 = random(var6,var7);
arr[var4]=var5;
var9 = 1;
i=i+var9;
goto lbl1;
lbl2:
var10 = 3;
printValue_Int_Unit(var10);
readInt();
return;
}
String toString_Int_String(int value) {
String var11;
var11 = intToString(value);
return var11;
}
void printValue_Int_Unit(int value) {
String var13;
var13 = toString_Int_String(value);
println(var13);
return;
}
