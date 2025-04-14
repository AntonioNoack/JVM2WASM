package highlevel

import utils.MethodSig

class InvokeFinalInstr(original: MethodSig, resolved: MethodSig, stackPushId: Int) :
    ResolvedMethodInstr(original, resolved, stackPushId)