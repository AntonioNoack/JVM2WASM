
#include <cstdint>
#include <bit>

typedef int32_t i32;
typedef int64_t i64;
typedef uint32_t u32;
typedef uint64_t u64;
typedef float f32;
typedef double f64;

struct f64i32 { f64 v0; i32 v1; };
struct f64i64 { f64 v0; i64 v1; };
struct i32f32 { i32 v0; f32 v1; };
struct i32f64 { i32 v0; f64 v1; };
struct i64f32 { i64 v0; f32 v1; };
struct i32i32 { i32 v0; i32 v1; };
struct i32i64 { i32 v0; i64 v1; };
struct i64f64 { i64 v0; f64 v1; };
struct i64i32 { i64 v0; i32 v1; };
struct i64i64 { i64 v0; i64 v1; };
struct f32f32 { f32 v0; f32 v1; };
struct f32f64 { f32 v0; f64 v1; };
struct f32i32 { f32 v0; i32 v1; };
struct f64f32 { f64 v0; f32 v1; };
struct f32i64 { f32 v0; i64 v1; };
struct f64f64 { f64 v0; f64 v1; };
struct i32i32i32 { i32 v0; i32 v1; i32 v2; };
struct i64i32i64 { i64 v0; i32 v1; i64 v2; };
struct f32i64f32i64 { f32 v0; i64 v1; f32 v2; i64 v3; };
struct f64i64f64i64 { f64 v0; i64 v1; f64 v2; i64 v3; };
struct i32i32i32i32 { i32 v0; i32 v1; i32 v2; i32 v3; };
struct i64i32i64i32 { i64 v0; i32 v1; i64 v2; i32 v3; };
struct f64f32f64f32 { f64 v0; f32 v1; f64 v2; f32 v3; };
struct f32f64f32f64 { f32 v0; f64 v1; f32 v2; f64 v3; };
struct f64f64f64f64 { f64 v0; f64 v1; f64 v2; f64 v3; };
struct i32f32i32f32 { i32 v0; f32 v1; i32 v2; f32 v3; };
struct i64f32i64f32 { i64 v0; f32 v1; i64 v2; f32 v3; };
struct i32i64i32i64 { i32 v0; i64 v1; i32 v2; i64 v3; };
struct i64i64i64i64 { i64 v0; i64 v1; i64 v2; i64 v3; };
struct f32i32f32i32 { f32 v0; i32 v1; f32 v2; i32 v3; };
struct i32f64i32f64 { i32 v0; f64 v1; i32 v2; f64 v3; };
struct f32f32f32f32 { f32 v0; f32 v1; f32 v2; f32 v3; };
struct f64i32f64i32 { f64 v0; i32 v1; f64 v2; i32 v3; };
struct i64f64i64f64 { i64 v0; f64 v1; i64 v2; f64 v3; };
