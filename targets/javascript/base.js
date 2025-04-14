
const encoder = new TextEncoder();
const wrapStringCache = {};

window.wrapString = function(str) {
    const cached = wrapStringCache[str];
    if(cached) return cached;
    // convert JavaScript-String into a JVM-String
    const string = wrapStringCache[str] = new java_lang_String()
    const charArray = new AC();
    const uint8Array = encoder.encode(str);
    charArray.values = new Int8Array(uint8Array.buffer);
    string.value = charArray;
    return string;
}