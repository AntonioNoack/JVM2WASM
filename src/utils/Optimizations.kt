package utils

/*
            txt = txt.replace(
                "(if (then\n" +
                        "  i32.const 0\n" +
                        "  i32.const 0\n" +
                        "  return\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "  i32.const 0\n" +
                        "  return\n" +
                        "))", "i32.eqz i32.const 0 return"
            )
            txt = txt.replace(
                "(if (result i32) (then\n" +
                        "  i32.const 0\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "))", "i32.eqz"
            )*/

// todo all replacement functions :)

// these somehow don't work :/
// they probably have different behaviours for NaN than we expect
/*txt = txt.replace(
    "call \$dcmpg\n" +
            "  (if (result i32) (then\n" +
            "  i32.const 0\n" +
            ") (else\n" +
            "  i32.const 1\n" +
            "))", "f64.eq i32.eqz"
)

txt = txt.replace(
    "call \$fcmpg\n" +
            "  (if (result i32) (then\n" +
            "  i32.const 0\n" +
            ") (else\n" +
            "  i32.const 1\n" +
            "))", "f32.eq i32.eqz"
)*/

/*   txt = txt.replace(
       "call \$lcmp\n" +
               "  (if (result i32) (then\n" +
               "  i32.const 0\n" +
               ") (else\n" +
               "  i32.const 1\n" +
               "))", "i64.eq"
   )

   txt = txt.replace("call \$fcmpg\n  i32.const 0 i32.ge_s", "f32.ge")
   txt = txt.replace("call \$dcmpg\n  i32.const 0 i32.ge_s", "f64.ge")
   txt = txt.replace("call \$fcmpg\n  i32.const 0 i32.gt_s", "f32.gt")
   txt = txt.replace("call \$dcmpg\n  i32.const 0 i32.gt_s", "f64.gt")
   txt = txt.replace("call \$fcmpg\n  i32.const 0 i32.le_s", "f32.le")
   txt = txt.replace("call \$dcmpg\n  i32.const 0 i32.le_s", "f64.le")
   txt = txt.replace("call \$fcmpg\n  i32.const 0 i32.lt_s", "f32.lt")
   txt = txt.replace("call \$dcmpg\n  i32.const 0 i32.lt_s", "f64.lt")
   txt = txt.replace("call \$fcmpg\n  i32.const 0 i32.eq", "f32.eq")
   txt = txt.replace("call \$dcmpg\n  i32.const 0 i32.eq", "f64.eq")

   // difference in effect? mmh...
   // if they all work, we have simplified a lot of places :)
   txt = txt.replace("call \$fcmpl\n  i32.const 0 i32.ge_s", "f32.ge")
   txt = txt.replace("call \$dcmpl\n  i32.const 0 i32.ge_s", "f64.ge")
   txt = txt.replace("call \$fcmpl\n  i32.const 0 i32.gt_s", "f32.gt")
   txt = txt.replace("call \$dcmpl\n  i32.const 0 i32.gt_s", "f64.gt")
   txt = txt.replace("call \$fcmpl\n  i32.const 0 i32.le_s", "f32.le")
   txt = txt.replace("call \$dcmpl\n  i32.const 0 i32.le_s", "f64.le")
   txt = txt.replace("call \$fcmpl\n  i32.const 0 i32.lt_s", "f32.lt")
   txt = txt.replace("call \$dcmpl\n  i32.const 0 i32.lt_s", "f64.lt")
   txt = txt.replace("call \$fcmpl\n  i32.const 0 i32.eq", "f32.eq")
   txt = txt.replace("call \$dcmpl\n  i32.const 0 i32.eq", "f64.eq")

   txt = txt.replace("call \$lcmp\n  i32.const 0 i32.gt_s", "i64.gt_s")
   txt = txt.replace("call \$lcmp\n  i32.const 0 i32.ge_s", "i64.ge_s")
   txt = txt.replace("call \$lcmp\n  i32.const 0 i32.lt_s", "i64.lt_s")
   txt = txt.replace("call \$lcmp\n  i32.const 0 i32.le_s", "i64.le_s")
   txt = txt.replace("call \$lcmp\n  i32.const 0 i32.eq", "i64.eq")

   txt = txt.replace(
       "i32.ne\n" +
               "  (if (result i32) (then\n" +
               "  i32.const 0\n" +
               ") (else\n" +
               "  i32.const 1\n" +
               "))", "i32.eq"
   )
   txt = txt.replace(
       "i32.eq\n" +
               "  (if (result i32) (then\n" +
               "  i32.const 0\n" +
               ") (else\n" +
               "  i32.const 1\n" +
               "))", "i32.ne"
   )

   txt = txt.replace(
       "call \$dcmpg\n" +
               "  (if (result i32) (then\n" +
               "  i32.const 0\n" +
               ") (else\n" +
               "  i32.const 1\n" +
               "))", "f64.eq"
   )
   txt = txt.replace(
       "call \$fcmpg\n" +
               "  (if (result i32) (then\n" +
               "  i32.const 0\n" +
               ") (else\n" +
               "  i32.const 1\n" +
               "))", "f32.eq"
   )

   txt = txt.replace(
       "call \$dupi32 (if (param i32) (then i32.const 0 call \$swapi32i32 return) (else drop))\n" +
               "  i32.const 0\n" +
               "  return", "return"
   )
   // why ever these constructs exist...
   txt = txt.replace(
       "i32.ne\n" +
               "  (if (param i32) (result i32 i32) (then\n" +
               "  i32.const 0\n" +
               ") (else\n" +
               "  i32.const 1\n" +
               "))", "i32.eq"
   )
   txt = txt.replace(
       "(if (result i32) (then\n" +
               "  i32.const 1\n" +
               ") (else\n" +
               "  i32.const 0\n" +
               "))", "i32.eqz i32.eqz"
   )

   txt = txt.replace(
       "call \$fcmpl\n" +
               "  (if", "f32.ne\n  (if"
   )
   txt = txt.replace(
       "call \$dcmpl\n" +
               "  (if", "f64.ne\n  (if"
   )
   txt = txt.replace(
       "call \$fcmpg\n" +
               "  (if", "f32.ne\n  (if"
   )
   txt = txt.replace(
       "call \$dcmpg\n" +
               "  (if", "f64.ne\n  (if"
   )

   txt = txt.replace(
       "call \$fcmpl\n" +
               "  i32.eqz", "f32.eq"
   )
   txt = txt.replace(
       "call \$dcmpl\n" +
               "  i32.eqz", "f64.eq"
   )
   txt = txt.replace(
       "call \$fcmpg\n" +
               "  i32.eqz", "f32.eq"
   )
   txt = txt.replace(
       "call \$dcmpg\n" +
               "  i32.eqz", "f64.eq"
   )
*/
