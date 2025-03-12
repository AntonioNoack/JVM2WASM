package utils


// todo all replacement functions :)

/*

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
*/
