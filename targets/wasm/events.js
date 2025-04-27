
    window.mouseX = 0
    window.mouseY = 0
    document.onmousemove = function(e){
        if (inited) {
            window.mouseX = e.clientX
            window.mouseY = e.clientY
            lib.EngineMouseMove(mouseX,mouseY)
        }
    }

    function mapMouseButton(i){
        return i == 2 ? 1 : i == 1 ? 2 : i;
    }

    document.onmousedown = function(e){
        if (inited) {
            lib.EngineKeyModState(calcMods(e))
            lib.EngineMouseDown(mapMouseButton(e.button))
            e.preventDefault()
        }
    }

    document.onmouseup = function(e) {
        if (inited) {
            lib.EngineKeyModState(calcMods(e))
            lib.EngineMouseUp(mapMouseButton(e.button))
            e.preventDefault()
        }
    }

    document.oncontextmenu = function(e){
        e.preventDefault()
        return false
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
        45:260,
        46:261,
    }

    document.onkeypress = function(e) {
        if(inited && e.keyCode != 116 && !(e.keyCode == 73 && e.shiftKey && e.ctrlKey)) {
            lib.EngineKeyModState(calcMods(e))
            lib.EngineKeyTyped(keyMap[e.keyCode] || e.keyCode)
            e.preventDefault()
        }
    }
    function calcMods(e){
        return (e.ctrlKey ? 2 : 0) + (e.shiftKey ? 1 : 0) + (e.altKey ? 4 : 0) + (e.metaKey ? 8 : 0)
    }
    document.onkeydown = function(e) {
        if(inited && e.keyCode != 116 && !(e.keyCode == 73 && e.shiftKey && e.ctrlKey)) {
            lib.EngineKeyModState(calcMods(e))
            lib.EngineKeyDown(keyMap[e.keyCode] || e.keyCode)
            if(e.key.length == 1 && !e.ctrlKey) lib.EngineCharTyped(e.key.charCodeAt(0), calcMods(e))
            e.preventDefault()
        }
    }
    document.onkeyup = function(e) {
        if(inited && e.keyCode != 116 && !(e.keyCode == 73 && e.shiftKey && e.ctrlKey)) {
            lib.EngineKeyModState(calcMods(e))
            lib.EngineKeyUp(keyMap[e.keyCode] || e.keyCode)
            e.preventDefault()
        }
    }
    document.onwheel = function(e) {
        if(inited) lib.EngineMouseWheel((e.deltaX||0)*0.01,(-e.deltaY||0)*0.01)
    }
