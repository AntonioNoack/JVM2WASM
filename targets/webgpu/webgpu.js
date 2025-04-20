// todo implement OpenGL using WebGPU, so we may use new/non-webgl features in the future

export const webGPU = {

	// objects
	framebuffers: [],
	renderbuffers: [],
	textures: [],
	buffers: [],
	queries: [],

	// state
	enabled: {},
	boundTextures: [],
	boundBuffers: {},

	boundProgram: null,
	readFB: null,
	writeFB: null,

	// constants:
	ARRAY_BUFFER: 34962,
	ELEMENT_ARRAY_BUFFER: 34963,
	MAX_SAMPLES: 36183,
	MAX_UNIFORM_COMPONENTS: 35658,
	MAX_FRAGMENT_UNIFORM_COMPONENTS: 35657,
	MAX_TEXTURE_IMAGE_UNITS: 34930,
	MAX_VERTEX_ATTRIBS: 34921,
	MAX_COLOR_ATTACHMENTS: 36063,
	MAX_TEXTURE_SIZE: 3379,

	enable: function(e) {
		this.enabled[e] = 1
	},
	disable: function(e) {
		this.enabled[e] = 0
	},
	init: async function(ctx) {
		this.ctx = ctx
		let adapter = this.adapter = await navigator.gpu.requestAdapter()
        let device = this.device = await adapter.requestDevice()

        this.preferredBackingFormat = navigator.gpu.getPreferredCanvasFormat()

        return this
	},
	getExtension: function(name) {
		return null
	},
	getParameter: function(id) {
		switch(id){
		case this.MAX_SAMPLES:
			return 1; // todo proper values
		case this.MAX_UNIFORM_COMPONENTS:
		case this.MAX_FRAGMENT_UNIFORM_COMPONENTS:
			return 1024;
		case this.MAX_TEXTURE_IMAGE_UNITS: 
			return 16;
		case this.MAX_VERTEX_ATTRIBS:
			return 16;
		case this.MAX_COLOR_ATTACHMENTS:
			return 8;
		case this.MAX_TEXTURE_SIZE:
			return 1024;
		default:
			throw new Error("Unknown getParameter: " + id)
		}
	},
	createTexture: function() {
		let o = { id: this.textures.length }
		this.textures.push(o)
		return o
	},
	deleteTexture: function(tex) {
		// todo implement
	},
	activeTexture: function(index) {
		this.activeTextureIndex = index
	},
	bindTexture: function(target, id) {
		this.boundTextures[this.activeTextureIndex] = { target, id }
	},
	blendEquationSeparate: function() {
		// todo implement
	},
	blendFuncSeparate: function() {
		// todo implement
	},
	createRenderbuffer: function() {
		let o = { id: this.renderbuffers.length }
    	this.renderbuffers.push(o)
    	return o
	},
	deleteRenderbuffer: function(rb) {
		// todo implement
	},
	bindRenderbuffer: function(target, rb) {
		this.rb = rb
	},
	renderbufferStorage: function(target, format, width, height) {

	},
	createFramebuffer: function() {
		let o = { id: this.framebuffers.length, targets: {} }
		this.framebuffers.push(o)
		return o
	},
	deleteFramebuffer: function(fb) {
		// todo implement
	},
	bindFramebuffer: function(target, fb) {
		this.readFB = fb
		this.writeFB = fb
	},
	readFramebuffer: function(target, fb) {
		this.readFB = fb
	},
	writeFramebuffer: function(target, fb) {
		this.writeFB = fb
	},
	texImage2D: function(target, level, format, w, h, border, dataFormat, dataType, data) {
		// todo implement
	},
	texSubImage2D: function(target, level, format, w, h, border, dataFormat, dataType, data) {
		// todo implement
	},
	texImage3D: function(target, level, format, w, h, d, border, dataFormat, dataType, data) {
		// todo implement
	},
	texParameteri: function() {
		// todo implement
	},
	framebufferTexture2D: function(target, attachment, target1, texture, level) {
		this.readFB.targets[attachment] = { texture, level }
	},
	framebufferRenderbuffer: function(target, attachment, target1, rb) {
		this.readFB.targets[attachment] = { rb }
	},
	checkFramebufferStatus: function(target) {
		return 36053 // complete
	},
    drawBuffer: function(modes) {
      	this.readFB.drawBuffers = modes
    },
	drawBuffers: function(modes) {
		this.readFB.drawBuffers = modes
	},
	createBuffer: function() {
		let o = { id: this.buffers.length }
		this.buffers.push(o)
		return o
	},
	bindBuffer: function(target, buffer) {
		this.boundBuffers[target] = buffer
	},
	bufferData: function(target, data, usage) {

		// todo destroy existing buffer

		// todo upload data

		if(target != this.ARRAY_BUFFER && target != this.ELEMENT_ARRAY_BUFFER)
			throw new Error('Unknown target: ' + target);

		const boundBuffer = this.boundBuffers[target];
	
		/* GPUBuffer */
		if (boundBuffer.buffer) {
			boundBuffer.buffer.destroy();
			boundBuffer.buffer = null;
		}

		const baseUsage = 
			target == this.ELEMENT_ARRAY_BUFFER ? GPUBufferUsage.INDEX 
												: GPUBufferUsage.VERTEX;
		const dataBufferDescriptor = {
			size: data.byteLength,
			usage: baseUsage | GPUBufferUsage.COPY_DST,
			mappedAtCreation: true
		};
		const buffer = boundBuffer.buffer = this.device.createBuffer(dataBufferDescriptor);

		const mapped = buffer.getMappedRange();
		const bufferView = 
			data instanceof Uint8Array ? new Uint8Array(mapped) :
			data instanceof Uint16Array ? new Uint16Array(mapped) :
			data instanceof Float32Array ? new Float32Array(mapped) :
			null;

		if (bufferView == null) {
			throw new Error('Unknown data type ' + data);
		}

		bufferView.set(data);
		buffer.unmap();
		
		boundBuffer.buffer = buffer;
		boundBuffer.usage = usage;
		
	},
	bufferSubData: function(target, offset, data) {
		const boundBuffer = this.boundBuffers[target];
		const buffer = boundBuffer.buffer;
		// Write to the buffer
		this.device.queue.writeBuffer(
			buffer,      // destination GPUBuffer
			offset,      // offset in the buffer (in bytes)
			data.buffer, // source data (must be ArrayBuffer or TypedArray.buffer)
			data.byteOffset, // offset in source (in bytes)
			data.byteLength // number of bytes to write
		);
    },
	viewport: function(x,y,w,h) {
		this.vp = { x, y, w, h }
	},
	clearColor: function(r,g,b,a) {
		this.cc = { r, g, b, a }
	},
	clearDepth: function(d) {
		this.cd = d
	},
	clear: function(mask) {
		// todo implement
	},
	createProgram: function() {
		return { attr: [] }
	},
	createShader: function(type) {
		return { type }
	},
	shaderSource: function(shader, src) {
		shader.src = src
	},
	compileShader: function() {},
	attachShader: function(program, shader) {
		program[shader.type] = shader
	},
	getShaderInfoLog: function(shader) {
		return ""
	},
	bindAttribLocation: function(program, index, name) {
		program.attr[index] = name
	},
	linkProgram: function(program) {},
	getProgramInfoLog: function(program) { return "" },
	getProgramParameter: function(program, type) {
		switch(type) {
			case 0x8B82: // link status
				return 1 // ok
			default:
				console.error('Unknown type', type)
				return 0
		}
	},
	useProgram: function(program) {
		this.boundProgram = program
	},
	getUniformLocation: function(program, name) {
		// todo resolve names
		return -1
	},
	uniform1i: function(ptr, x) {},
	uniform1f: function(ptr, x) {},
	uniform1fv: function(ptr, vs) {},
	uniform2i: function(ptr, x, y) {},
	uniform2f: function(ptr, x, y) {},
	uniform3f: function(ptr, x, y, z) {},
	uniform4f: function(ptr, x, y, z, w) {
		// todo implement
	},
	uniform4fv: function(ptr, vs) {
		// todo implement
	},
	uniformMatrix4fv: function() {
		// todo implement
	},
	uniformMatrix4x3fv: function() {
		// todo implement
	},
	vertexAttribPointer: function() {
		// todo implement
	},
	vertexAttribDivisor: function() {
		// todo implement
	},
	depthFunc: function() {},
	depthRange: function() {},
	depthMask: function(v) {
		this.writeDepth = v
	},
	cullFace: function(m) {
		this.culling = m
	},
	enableVertexAttribArray: function() {},
	disableVertexAttribArray: function() {},
	vertexAttrib1f: function() {}, // only used for zero
	bindVertexArray: function() {
		// not used afaik
	},
	generateMipmap: function() {
		// todo implement
	},
	drawArrays: function() {
		// todo implement
	},
	drawElements: function() {
		// todo implement
	},
	drawArraysInstanced: function() {
		// todo implement
	},
	drawElementsInstanced: function() {
		// todo implement
	},
	scissor: function(x,y,w,h) {
		this.scissorRect = { x, y, w, h }
	},
	flush: function() {},
	finish: function() {},
	readPixels: function(x,y,w,h,format,type,dst) {},
	createVertexArray: function() {
		// not actually used much 
		return {}; 
	},
	createQuery: function() {
		let o = { id: this.queries.length }
		this.queries.push(o)
		return o
	},
}
