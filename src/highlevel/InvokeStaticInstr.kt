package highlevel

import utils.MethodSig

class InvokeStaticInstr(original: MethodSig, resolved: MethodSig, stackPushId: Int) :
    ResolvedMethodInstr(original, resolved, stackPushId)