package engine.test;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

public class LuaTest {
	public static void test() {
		// todo this crashes, because LuaTable.countIntKeys() probably NOVALS=null -> NPE
		LuaValue v = LuaInteger.valueOf(3);
		LuaValue w = LuaInteger.valueOf(15);
		System.out.println(w.mul(v));
		Globals globals = JsePlatform.standardGlobals();
		LuaTable t;
		LuaValue f = globals.load("return 45*4");
		System.out.println(f);
		System.out.println(f.call());
	}
}
