
"use-strict";

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const wrapStringCache = new Map();

window.wrapString = function(str) {
    str = String(str);
    const cached = wrapStringCache.get(str);
    if(cached) return cached;
    // convert JavaScript-String into a JVM-String
    const string = new java_lang_String()
    const charArray = new AB();
    const uint8Array = encoder.encode(str);
    charArray.values = new Int8Array(uint8Array.buffer);
    string.value = charArray;
    wrapStringCache.set(str,string);
    return string;
}

window.unwrapString = function(str) {
    const chars = str.value.values;
    return decoder.decode(chars);
}

window.getJSClassByClass = function(clazz) {
    return clazz.jsClass;
}

window.getJSClassById = function(classId) {
    return getJSClassByClass(CLASS_INSTANCES[classId]);
}

let annotaCtr = 0;
window.annota = function(classId) {
    const clazz = getJSClassById(classId);
    ANNOTATION_INSTANCES[annotaCtr++] = new clazz();
}

let linkCtr = 0;
let initCtr = 0;
window.link = function(jvmName, jsClass){
    let classId = linkCtr++;
    let jvmClass = CLASS_INSTANCES[classId];
    jsClass.CLASS_INSTANCE = jvmClass;
    jvmClass.jsClass = jsClass;
    jvmClass.index = classId;
    
    jvmClass.name = wrapString(jvmName);
    const sni = jvmName.lastIndexOf('/');
    const simpleName = jvmName.substring(sni+1);
    jvmClass.simpleName = wrapString(simpleName);
}

window.init = function(superId,fields,methods){
    let classId = initCtr++;
    createClass(classId,superId,fields,methods);
}

function createClass(classId,superId,fields,methods) {
    const clazz = CLASS_INSTANCES[classId];
    clazz.superClass = superId != classId ? CLASS_INSTANCES[superId] : null;
    fields = fields.split(';');
    const fields1 = clazz.fields = new AW();
    const fields2 = fields1.values = superId < classId ? [...CLASS_INSTANCES[superId].fields.values] : [];
    for(let i=0;i<fields.length;i++){
        if(fields[i].length<3) continue;
        const field = fields[i].split(',');
        const field1 = new java_lang_reflect_Field();
        field1.name = wrapString(field[0]);
        field1.type = CLASS_INSTANCES[field[1]*1];
        field1.clazz = clazz;
        field1.modifiers = field[2]*1;
        field1.annotations = new AW();
        field1.annotations.values = field.slice(3).map(id => ANNOTATION_INSTANCES[id]);
        fields2.push(field1);
    }
    methods = methods.split(';');
    const methods1 = clazz.methods = new AW();
    const methods2 = methods1.values = [];
    const constructors1 = clazz.constructors = new AW();
    const constructors2 = constructors1.values = [];
    for(let i=0;i<methods.length;i++) {
        const method = methods[i].split(',');
        const jvmName = method[0];
        const isConstructor = jvmName.length == 0;
        const method1 = isConstructor
            ? new java_lang_reflect_Constructor()
            : new java_lang_reflect_Method();
        method1.name = wrapString(jvmName);
        method1.jsName = method[1];
        method1.modifiers = method[2]*1;
        method1.clazz = clazz;
        method1.parameterTypes = new AW();
        method1.parameterTypes.values = method.slice(4).map(id => CLASS_INSTANCES[id]);
        method1.returnType = CLASS_INSTANCES[method[3]*1] || null;
        const dstList = isConstructor ? constructors2 : methods2;
        dstList.push(method1);
    }
    return clazz;
 }
 
 const bitcastBuffer = new ArrayBuffer(8);
 const bitcastF32 = new Float32Array(bitcastBuffer);
 const bitcastF64 = new Float64Array(bitcastBuffer);
 const bitcastI32 = new Int32Array(bitcastBuffer);
 const bitcastI64 = new BigInt64Array(bitcastBuffer);

 window.getF32Bits = function(arg0){
    bitcastF32[0] = arg0;
    return bitcastI32[0];
 }
 
 window.getF64Bits = function(arg0){
    bitcastF64[0] = arg0;
    return bitcastI64[0];
 }
 
 window.fromF32Bits = function(arg0){
    bitcastI32[0] = arg0;
    return bitcastF32[0];
 }
 
 window.fromF64Bits = function(arg0){
    bitcastI64[0] = arg0;
    return bitcastF64[0];
 }

 window.unreachable = function(funcName) {
    throw new Error('Unreachable in "' + funcName + '"');
 }

 // todo fill this buffer with everything necessary
 let resourcesAsBuffers = new Map();
 window.getResourceAsStream = function(jvmName) {
    const jsName = unwrapString(jvmName);
    const result = new java_io_ByteArrayInputStream();
    let bytes = resourcesAsBuffers.get();
    if(!bytes) {
        console.log('Looked for missing resource "' + jsName + '"');
        bytes = new ArrayBuffer(0);
    }
    const byteArray = new AB();
    byteArray.values = new Uint8Array(bytes);
    result.new_ABV(byteArray);
    return result;
 }