
const startTime = Date.now()
const lib = window
window.lib = window
window.inited = false

try {

    window.commandLine = [[],[]]

    window.str = window.unwrapString

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
        buffer = buffer.values;
        str = str + ""; // make sure it's a string
        let l = Math.max(Math.min(buffer.length, str.length), 0)
        for(let i=0;i<l;i++) {
            buffer[i] = str.charCodeAt(i);
        }
        return l
    }

    window.find = function(word) {
        for(let key in gl) if(gl[key] == word) return key
    }

    window.stop = false
    function startEngine() {

        if(window.inited) return

        console.log("Starting engine")
        canvas.width = window.innerWidth
        canvas.height = window.innerHeight
        window.gl = canvas.getContext("webgl2",{premultipliedAlpha:false,alpha:false,antialias:false});
        if(!gl) pleaseWait.innerText = "WebGL is not supported!"

        /*var supportsFP16 = !!*/gl.getExtension("EXT_color_buffer_half_float")
        /*var supportsFP32 = !!*/gl.getExtension("EXT_color_buffer_float")

        console.log("Calling main function")
        safe(lib.EngineMain(0))
        console.log("Called main function")

        window.lastTime = 0
        window.inited = true
        function render(time) {
            if(window.hasCrashed > 20) window.stop = true
            if(canvas.width != innerWidth || canvas.height != innerHeight) {
                console.log("Resolution changed")
                canvas.width = innerWidth
                canvas.height = innerHeight
            }
            var dt = (time-lastTime) * 1e-3;
            
            if (!webXR.hasXRSession) {
                safe(lib.EngineUpdate(innerWidth, innerHeight, dt));
            }// else console.log('Skipping EngineUpdate, because XR is active')
            
            window.lastTime = time
            if (!window.stop) requestAnimationFrame(render)
        }
        requestAnimationFrame(render)
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

    window.delmap = function(id){
        glMap[id] = ""
    }

    window.hasCrashed = 0

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

    function onLoaded() {
        pleaseWait.style.display='none'
        window.objectOverhead = lib.oo()
    	window.arrayOverhead = objectOverhead + 4
    	console.log("calling lib.init()")
    	safe(lib.init())
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
        for(let i=0;i<l;i++){
            let e = entries[i]
            highest[i+'.'+e[0]+'.'+findClass(e[0])]=e[1]
        }
        console.log(highest)
        console.log('total:', total)
    }
    window.flipImageVertically = function(w,h,buffer){
        let rowSize = w * 4; // 4 bytes per pixel (RGBA)
        let tempRow = new Uint8ClampedArray(rowSize);

        for (let y = 0, h2 = Math.floor(h / 2); y < h2; y++) {
            let topOffset = y * rowSize;
            let bottomOffset = (h - 1 - y) * rowSize;

            // Swap the top and bottom rows
            tempRow.set(buffer.subarray(topOffset, topOffset + rowSize));
            buffer.copyWithin(topOffset, bottomOffset, bottomOffset + rowSize);
            buffer.set(tempRow, bottomOffset);
        }
    }

    window.gcLock = function(ref){}
    window.gcUnlock = function(ref){}
    window.markJSReferences = function(){}

    // alpha is disabled, so we get subpixel rendering
    // https://stackoverflow.com/questions/4550926/subpixel-anti-aliased-text-on-html5s-canvas-element
    window.ctx = txtCanvas.getContext('2d', {alpha: false, willReadFrequently: true})

    // querying fonts: console.log(await queryLocalFonts());, works but popup
    loadResources(onLoaded)

} catch(e){
	console.error(e)
	document.body.innerText = e
}