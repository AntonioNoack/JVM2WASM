
// not really well supported yet
let useWebGPU = false

let module = null
let instance = null
let startTime = Date.now()

import { autoJS } from "./wasm/index0.js";
import { webGPU } from './webgpu.js';

window.inited = false

try {

    var memory = window.memory = new WebAssembly.Memory({
        initial: autoJS.initialMemorySize,
        // maximum: 100
    });

    var imports = {
        jvm: {
            // todo handle NaN correctly
            fcmpl(a,b){ return (a > b ? 1 : 0) - (a < b ? 1 : 0) }, // none of these can throw
            fcmpg(a,b){ return (a > b ? 1 : 0) - (a < b ? 1 : 0) },
            dcmpl(a,b){ return (a > b ? 1 : 0) - (a < b ? 1 : 0) },
            dcmpg(a,b){ return (a > b ? 1 : 0) - (a < b ? 1 : 0) },
        },
        js: { mem: memory }
    }

    var jvm = imports.jvm
    for (var key in autoJS) {
        jvm[key] = autoJS[key]
    }

    window.commandLine = [[],[]]

    window.str = function(x) {
        // convert Java string into JavaScript string
        if(x == 0) return null
        if(lib.rCl(x) == 10) {
            let chars = lib.r32(x + objectOverhead + 4)
            if(chars == 0) return null
            let cl = lib.rCl(chars)
            if(cl == 6) {
                let length = lib.r32(chars + objectOverhead)
                if(length < 0 || length > 65535) return "STRING TOO LONG OR SHORT11!!: "+length
                let str = []
                for(var i=0;i<length;i++){
                    str.push(String.fromCharCode(lib.r16(chars + arrayOverhead + (i+i))))
                }
                return str.join("")
            } else if(cl == 5) {
                let length = lib.r32(chars + objectOverhead)
                if(length < 0 || length > 65535) return "STRING TOO LONG OR SHORT11!!: "+length
                return new TextDecoder().decode(new Uint8Array(memory.buffer, chars + arrayOverhead, length));
            } else throw "INCORRECT CLASS IN STRING.CHARS!!!"
        } else throw "INVALID_STRING!!1! at " + x + ", class " + lib.rCl(x)
        return x;
    }

    window.ptr2err = function(ptr){
        var msg = str(lib.r32(ptr + objectOverhead)) // 0 = offset of "detailMessage"
        var name = str(lib.java_lang_Class_getName_Ljava_lang_String(lib.findClass(lib.rCl(ptr)))[0])
        return msg ? name+": "+msg : name
    }

    window.cls2err = function(cls){
        return str(lib.java_lang_Class_getName_Ljava_lang_String(lib.findClass(cls))[0])
    }

    window.trace = function(th){
        if(th == 0) return null
        if(lib.instanceOf(th, 14)){// 14 = Throwable
            var clazz = ptr2err(th)
            var trace = lib.r32(th + objectOverhead + 4) // first is message
            if(trace && lib.instanceOf(trace, 1)){
                var traceLength = lib.r32(trace + objectOverhead)
                var trace1 = []
                for(var i=0;i<traceLength;i++){
                    var element = lib.r32(trace + arrayOverhead + 4 * i)
                    if(element && lib.rCl(element) == 15){
                        trace1.push(
                            str(lib.r32(element + objectOverhead))+'.'+
                            str(lib.r32(element + objectOverhead + 4))+'(:'+
                            lib.r32(element + objectOverhead + 12)+')'
                        )
                    } else trace1.push(null)
                }
                trace = trace1
            }
            console.log(clazz, trace)
        } else console.error('Not a Throwable', lib.rCl(th))
        return th
    }

    window.trace1 = function(trace){
        if(trace && lib.instanceOf(trace, 1)){
            var traceLength = lib.r32(trace + objectOverhead)
            var trace1 = []
            for(var i=0;i<traceLength;i++){
                var element = lib.r32(trace + arrayOverhead + 4 * i)
                if(element && lib.rCl(element) == 15){
                    trace1.push(
                        str(lib.r32(element + objectOverhead))+'.'+
                        str(lib.r32(element + objectOverhead + 4))+'(:'+
                        lib.r32(element + objectOverhead + 12)+')'
                    )
                } else trace1.push(null)
            }
            console.log(trace1)
        }
    }

    window.fill = function(buffer, str) {
        if(buffer == 0) return 0
        str = str + ""
        var l = Math.max(Math.min(lib.r32(buffer + objectOverhead), str.length), 0)
        for(var i=0;i<l;i++) {
            lib.w16(buffer + arrayOverhead + i+i, str.charCodeAt(i))
        }
        return l
    }

    window.find = function(word) {
        for(key in gl) if(gl[key] == word) return key
    }

    // var depthTexExt = gl.getExtension("WEBGL_depth_texture")
        // console.log('depth-tex-ext:', depthTexExt)

        /*
        "EXT_color_buffer_float"
        "EXT_color_buffer_half_float"
        "EXT_disjoint_timer_query_webgl2"
        "EXT_float_blend"
        "EXT_texture_compression_bptc"
        "EXT_texture_compression_rgtc"
        "EXT_texture_filter_anisotropic"
        "EXT_texture_norm16"
        "KHR_parallel_shader_compile"
        "OES_draw_buffers_indexed"
        "OES_texture_float_linear"
        "WEBGL_compressed_texture_s3tc"
        "WEBGL_compressed_texture_s3tc_srgb"
        "WEBGL_debug_renderer_info"
        "WEBGL_debug_shaders"
        "WEBGL_lose_context"
        "WEBGL_multi_draw"
        "OVR_multiview2"
        */

    window.stop = false
    var t0 = new Date().getTime()
    window.gcCtr = 0
    window.gcStage = 0
    async function startEngine() {

        if(window.inited) return

        console.log("Starting engine")
        canvas.width = window.innerWidth
        canvas.height = window.innerHeight
        window.gl = useWebGPU ?
        	await webGPU.init(canvas.getContext("webgpu")) :
        	canvas.getContext("webgl2",{premultipliedAlpha:false,alpha:false,antialias:false});
        if(!gl) pleaseWait.innerText = "WebGL is not supported!"

        /*var supportsFP16 = !!*/gl.getExtension("EXT_color_buffer_half_float")
        /*var supportsFP32 = !!*/gl.getExtension("EXT_color_buffer_float")
        gl.getExtension('WEBGL_color_buffer_float')

        console.log("Calling main function")
        safe(lib.engine_Engine_main_Ljava_lang_StringV(0))
        console.log("Called main function")

        var fi = 0
        var lastTime = 0
        var fakeFpsMultiplier = 1 // not really working, the engine is rendering only necessary frames 😅
        window.inited = true
        function render(time) {
            // console.log("Rendering frame", time)
            if(window.ec > 20) window.stop = true
            if(canvas.width != innerWidth || canvas.height != innerHeight) {
                console.log("Resolution changed")
                canvas.width = innerWidth
                canvas.height = innerHeight
            }
            var dt = (time-lastTime)*1e-3/fakeFpsMultiplier
            for(var i=0;i<fakeFpsMultiplier;i++) {
                // console.log("Calling Engine.update")
                safe(lib.engine_Engine_update_IIFV(innerWidth, innerHeight, dt))
            }
            lastTime = time
            if (window.gcCtr++ >= 2000) {
                if (window.gcStage == 0) {
                    lib.concurrentGC0();
                    window.gcStage = 1;
                } else if (lib.concurrentGC1()) {
                    window.gcStage = 0;
                    window.gcCtr = 0;
               } // else stage stays the same
            }
            if(!window.stop) requestAnimationFrame(render)
        }
        requestAnimationFrame(render)
    }

    window.mouseX = 0
    window.mouseY = 0
    document.onmousemove = function(e){
        window.mouseX = e.clientX
        window.mouseY = e.clientY
        if(lib && inited) lib.engine_Engine_mouseMove_FFV(mouseX,mouseY)
    }

    function mapMouseButton(i){
        return i == 2 ? 1 : i == 1 ? 2 : i;
    }

    document.onmousedown = function(e){
        if(lib && inited) {
            lib.engine_Engine_keyModState_IV(calcMods(e))
            lib.engine_Engine_mouseDown_IV(mapMouseButton(e.button))
            e.preventDefault()
        }
    }

    document.onmouseup = function(e) {
        if(lib && inited) {
            lib.engine_Engine_keyModState_IV(calcMods(e))
            lib.engine_Engine_mouseUp_IV(mapMouseButton(e.button))
            e.preventDefault()
        }
    }

    document.oncontextmenu = function(e){
        if(lib && inited){
            lib.engine_Engine_mouseDown_IV(1)
            lib.engine_Engine_mouseUp_IV(1)
            e.preventDefault()
            return false
        }
    }

    // todo copy paste events

    var keyMap = {
        // arrows
        37:263,
        39:262,
        38:265,
        40:264,
        // backspace
        8:259,
        // shift,control,alt,super,
        16:340,
        17:341,
        18:342,
        91:343,
        // keypad
        111:47,
        106:332,
        109:333,
        107:334,
        144:282, // num lock
        // keypad numbers
        96:320,
        97:321,
        98:322,
        99:323,
        100:324,
        101:325,
        102:326,
        103:327,
        104:328,
        105:329,
        // enter, escape, tab
        13:257,
        27:256,
        9:258,
        // comma, minus, dot
        188:44,
        189:45,
        190:46,
        // hash-tag
        // 191: idk how it's called in GLFW :/
        // question mark..
        // 219:
        33:266,
        34:267,
        36:268,
        35:269,
        37:270,
        38:271,
        45:260,
        46:261,
    }

    document.onkeypress = function(e) {
        if(lib && inited && e.keyCode != 116 && !(e.keyCode == 73 && e.shiftKey && e.ctrlKey)) {
            lib.engine_Engine_keyModState_IV(calcMods(e))
            lib.engine_Engine_keyTyped_IV(keyMap[e.keyCode] || e.keyCode)
            e.preventDefault()
        }
    }
    function calcMods(e){
        return (e.ctrlKey ? 2 : 0) + (e.shiftKey ? 1 : 0) + (e.altKey ? 4 : 0) + (e.metaKey ? 8 : 0)
    }
    document.onkeydown = function(e) {
        if(lib && inited && e.keyCode != 116 && !(e.keyCode == 73 && e.shiftKey && e.ctrlKey)) {
            lib.engine_Engine_keyModState_IV(calcMods(e))
            lib.engine_Engine_keyDown_IV(keyMap[e.keyCode] || e.keyCode)
            if(e.key.length == 1) lib.engine_Engine_charTyped_IIV(e.key.charCodeAt(0), calcMods(e))
            e.preventDefault()
        }
    }
    document.onkeyup = function(e) {
        if(lib && inited && e.keyCode != 116 && !(e.keyCode == 73 && e.shiftKey && e.ctrlKey)) {
            lib.engine_Engine_keyModState_IV(calcMods(e))
            lib.engine_Engine_keyUp_IV(keyMap[e.keyCode] || e.keyCode)
            e.preventDefault()
        }
    }
    document.onwheel = function(e) {
        if(lib && inited) lib.engine_Engine_mouseWheel_FFV((e.deltaX||0)*0.01,(-e.deltaY||0)*0.01)
    }

    // todo touch events

    const glMap = window.glMap = [null]
    window.glTimeQueries = {}
    window.glTimeQuery = 0
    window.glTimer = 0
    window.map = function(v){
        var id = glMap.length
        glMap[id] = v
        return id
    }

    window.unmap = function(id){
        return glMap[id]
    }

    window.ec = 0
    window.lib = 0
    var fetched = fetch("wasm/linux.wasm")
	fetched
        .then(response => response.arrayBuffer())
        .then(buffer => WebAssembly.instantiate(buffer, imports))
        .then(data => setTimeout(x => onLoaded(data), 0))

    window.measureText = function(font,size,text){
        window.ctx.font=(size|0)+'px '+str(font)
        return window.tmp=window.ctx.measureText(text)
    }

    function safe(x) {
        if(x) {
            window.inited = false
            throw trace(x)
        }
    }
    window.safe = safe

    function onLoaded(results){
        window.results = results
        window.module = results.module
        window.instance = results.instance
        window.lib = results.instance.exports
        pleaseWait.style.display='none'
        window.objectOverhead = lib.oo()
    	window.arrayOverhead = objectOverhead + 4
    	console.log("calling lib.init()")
    	safe(lib.init())
    	console.log("calling lib.gc()")
        safe(lib.gc())
        var sleep = Math.max(0, startTime - Date.now() + 300)
        console.log('Showing logo for '+sleep+' ms')
        setTimeout(startEngine, sleep)
    }

    window.calloc = {}
    window.findClass = function(id){
        return str(lib.java_lang_Class_getName_Ljava_lang_String(lib.findClass(id))[0])
    }
    window.sortCalloc = function(){
        var entries = []
        var total = 0
        for(var k in window.calloc){
            entries.push([k,window.calloc[k]])
            total += window.calloc[k]
        }
        entries.sort((a,b) => b[1]-a[1])
        var l = Math.min(20, entries.length)
        var highest = {}
        for(var i=0;i<l;i++){
            var e = entries[i]
            highest[i+'.'+e[0]+'.'+findClass(e[0])]=e[1]
        }
        console.log(highest)
        console.log('total:', total)
    }

    var refs = window.jsRefs = {}
    window.gcLock = function(ref){
    	jsRefs[ref] = (jsRefs[ref]||0)+1;
    }
    window.gcUnlock = function(ref){
    	if(jsRefs[ref] == 1) delete jsRefs[ref];
        else jsRefs[ref] = (jsRefs[ref]||0)-1;
    }
    window.markJSReferences = function(){
        for(var ref in jsRefs) {
            lib.gcMarkUsed(ref)
        }
    }

    // alpha is disabled, so we get subpixel rendering
    // https://stackoverflow.com/questions/4550926/subpixel-anti-aliased-text-on-html5s-canvas-element
    window.ctx = txtCanvas.getContext('2d', {alpha: false, willReadFrequently: true})

    // querying fonts: console.log(await queryLocalFonts());, works but popup

} catch(e){
	console.error(e)
	document.body.innerText = e
}