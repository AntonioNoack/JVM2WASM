package highlevel

import utils.MethodSig

class InvokeSpecialInstr(original: MethodSig, resolved: MethodSig, stackPushId: Int) :
    ResolvedMethodInstr(original, resolved, stackPushId)