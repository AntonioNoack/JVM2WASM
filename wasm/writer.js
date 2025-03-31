
import { autoJS } from "./index0.js";

try {

    var memory = window.memory = new WebAssembly.Memory({
        initial: autoJS.initialMemorySize,
    });

    var imports = {
        jvm: {
            "fcmpg": function(){},
            "fcmpl": function(){},
            "dcmpg": function(){},
            "dcmpl": function(){},
        },
        js: { mem: memory }
    }

    var jvm = imports.jvm
    for (var key in autoJS) {
        jvm[key] = autoJS[key]
    }

    fetch("jvm2wasm.test.wasm")
        .then(response => response.arrayBuffer())
        .then(buffer => WebAssembly.instantiate(buffer, imports))
        .then(data => setTimeout(x => onLoaded(data), 0))

    function onLoaded(results){
        window.results = results
        window.module = results.module
        window.instance = results.instance
        window.lib = results.instance.exports
    }
} catch(e){
	console.error(e)
	document.body.innerText = e
}