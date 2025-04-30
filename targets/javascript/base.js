
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

window.getClassById = function(classId) {
    return CLASS_INSTANCES[classId];
}

window.getJSClassByClass = function(clazz) {
    return clazz.jsClass;
}

window.getJSClassById = function(classId) {
    return getJSClassByClass(CLASS_INSTANCES[classId]);
}

window.fillArray = function(arr,i0,i1,value) {
    for(let i=i0;i<i1;i++) arr[i] = value;
}

window.applyBoxing = function(value, type) {
    let paramType = type.index;
    switch (paramType) {
        case 16: return java_lang_Integer.valueOf_ILjava_lang_Integer(value);
        case 17: return java_lang_Long.valueOf_JLjava_lang_Long(value);
        case 18: return java_lang_Float.valueOf_FLjava_lang_Float(value);
        case 19: return java_lang_Double.valueOf_DLjava_lang_Double(value);
        case 20: return java_lang_Boolean.valueOf_ZLjava_lang_Boolean(value);
        case 21: return java_lang_Byte.valueOf_BLjava_lang_Byte(value);
        case 22: return java_lang_Short.valueOf_SLjava_lang_Short(value);
        case 23: return java_lang_Character.valueOf_CLjava_lang_Character(value);
        case 24: return undefined;
    }
    if(value && typeof value != 'object') {
        console.log(value, type);
        throw new Error('Cannot leave unboxed');
    }
    return value;
}

window.applyUnboxing = function(value, type) {
    let paramType = type.index;
    if (paramType >= 16 && paramType <= 24) {
        if(value.value === undefined) {
            console.log(value, type);
            throw new Error('Cannot unbox undefined');
        }
        return value.value;
    }
    return value;
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
    const sni = jvmName.lastIndexOf('.');
    const simpleName = jvmName.substring(sni+1);
    jvmClass.simpleName = wrapString(simpleName);
}

window.init = function(superId,interfaces,fields,methods){
    let classId = initCtr++;
    createClass(classId,superId,interfaces,fields,methods);
}

window.unpackFloatArray = function(buffer,length) {
    if(!(buffer.values instanceof Float32Array) || buffer.values.length < length)
        throw new Error('Size/type mismatch');
    return buffer.values;
}

window.unpackIntArray = function(buffer,length) {
    if(!(buffer.values instanceof Int32Array) || buffer.values.length < length)
        throw new Error('Size/type mismatch');
    return buffer.values;
}

window.toFloat32Array = function(buffer, newLength) {
    let result = buffer instanceof Float32Array ?
        buffer : new Float32Array(buffer.buffer, buffer.byteOffset, buffer.byteLength >> 2);
    if (result.byteLength != buffer.byteLength) throw new Error("Size mismatch!");
    if (newLength !== undefined && newLength < (result.byteLength >> 2)) {
        result = new Float32Array(buffer.buffer, buffer.byteOffset, newLength);
    }
    return result;
}

window.toUint32Array = function(buffer) {
    let result = buffer instanceof Uint32Array ?
        buffer : new Uint32Array(buffer.buffer, buffer.byteOffset, buffer.byteLength >> 2);
    if(result.byteLength != buffer.byteLength) throw new Error("Size mismatch!");
    return result;
}

window.toUint16Array = function(buffer) {
    let result = buffer instanceof Uint16Array ?
        buffer : new Uint16Array(buffer.buffer, buffer.byteOffset, buffer.byteLength >> 1);
    if(result.byteLength != buffer.byteLength) throw new Error("Size mismatch!");
    return result;
}

window.toUint8Array = function(buffer) {
    let result = buffer instanceof Uint8Array ?
        buffer : new Uint8Array(buffer.buffer, buffer.byteOffset, buffer.byteLength);
    if(result.byteLength != buffer.byteLength) throw new Error("Size mismatch!");
    return result;
}

function createClass(classId,superId,interfaces,fields,methods) {
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
    methods = methods.length ? methods.split(';') : [];
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
    clazz.interfaceIds = interfaces
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
const resourcesAsBuffers = new Map();
window.getResourceAsByteArray = function(jvmName) {
    const jsName = unwrapString(jvmName);
    let bytes = resourcesAsBuffers.get(jsName);
    if(!bytes) return null;
    const byteArray = new AB();
    byteArray.values = new Uint8Array(bytes);
    // console.log('Returned', bytes, 'as stream for', jsName);
    return byteArray;
}

window.loadResources = function(callback) {
    let urls = ['saveables.yaml']
    const prefix = '../../assets/'
    // Create an array of fetch promises
    const fetchPromises = urls.map(url =>
        fetch(prefix + url)
        .then(response => {
            if (!response.ok) throw new Error(`Failed to load ${url}`);
            return response.arrayBuffer();
        })
        .then(buffer => {
            return { url, buffer }
        })
    );

    // Wait for all promises to resolve
    Promise.all(fetchPromises)
        .then(resources => {
            resources.forEach(resource => {
                const { url, buffer } = resource;
                resourcesAsBuffers.set(url, buffer);
            });
            callback();
        })
        .catch(err => {
            console.error('Error loading resources :(', err)
        });
}