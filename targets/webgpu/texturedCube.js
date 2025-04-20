
// from https://webkit.org/demos/webgpu/scripts/textured-cube.js

async function renderTexturedCube() {
	if (!navigator.gpu || GPUBufferUsage.COPY_SRC === undefined) {
		document.body.className = 'error';
		return;
	}

	const canvas = document.querySelector("canvas");
	canvas.width = window.innerWidth;
	canvas.height = window.innerHeight;
	
	const adapter = await navigator.gpu.requestAdapter();
	const device = await adapter.requestDevice();
	
	const preferredBackingFormat = navigator.gpu.getPreferredCanvasFormat();
	/*** Vertex Buffer Setup ***/
	
	/* Vertex Data */
	const vertexStride = 8 * 4;
	const vertexDataSize = vertexStride * 36;
	
	/* GPUBufferDescriptor */
	const vertexDataBufferDescriptor = {
		size: vertexDataSize,
		usage: GPUBufferUsage.VERTEX,
		mappedAtCreation: true
	};

	/* GPUBuffer */
	const vertexBuffer = device.createBuffer(vertexDataBufferDescriptor);
	const vertexWriteArray = new Float32Array(vertexBuffer.getMappedRange());
	vertexWriteArray.set([
		// float3 position, float3 color, float2 uv
		+.5, -.5, +.5,  1, 0, 1,  1, 1,
		-.5, -.5, +.5,  0, 0, 1,  0, 1,
		-.5, -.5, -.5,  0, 0, 0,  0, 0,
		+.5, -.5, -.5,  1, 0, 0,  1, 0,
		+.5, -.5, +.5,  1, 0, 1,  1, 1,
		-.5, -.5, -.5,  0, 0, 0,  0, 0,

		+.5, +.5, +.5,  1, 1, 1,  1, 1,
		+.5, -.5, +.5,  1, 0, 1,  0, 1,
		+.5, -.5, -.5,  1, 0, 0,  0, 0,
		+.5, +.5, -.5,  1, 1, 0,  1, 0,
		+.5, +.5, +.5,  1, 1, 1,  1, 1,
		+.5, -.5, -.5,  1, 0, 0,  0, 0,

		-.5, +.5, +.5,  0, 1, 1,  1, 1,
		+.5, +.5, +.5,  1, 1, 1,  0, 1,
		+.5, +.5, -.5,  1, 1, 0,  0, 0,
		-.5, +.5, -.5,  0, 1, 0,  1, 0,
		-.5, +.5, +.5,  0, 1, 1,  1, 1,
		+.5, +.5, -.5,  1, 1, 0,  0, 0,

		-.5, -.5, +.5,  0, 0, 1,  1, 1,
		-.5, +.5, +.5,  0, 1, 1,  0, 1,
		-.5, +.5, -.5,  0, 1, 0,  0, 0,
		-.5, -.5, -.5,  0, 0, 0,  1, 0,
		-.5, -.5, +.5,  0, 0, 1,  1, 1,
		-.5, +.5, -.5,  0, 1, 0,  0, 0,

		+.5, +.5, +.5,  1, 1, 1,  1, 1,
		-.5, +.5, +.5,  0, 1, 1,  0, 1,
		-.5, -.5, +.5,  0, 0, 1,  0, 0,
		-.5, -.5, +.5,  0, 0, 1,  0, 0,
		+.5, -.5, +.5,  1, 0, 1,  1, 0,
		+.5, +.5, +.5,  1, 1, 1,  1, 1,
	
		+.5, -.5, -.5,  1, 0, 0,  1, 1,
		-.5, -.5, -.5,  0, 0, 0,  0, 1,
		-.5, +.5, -.5,  0, 1, 0,  0, 0,
		+.5, +.5, -.5,  1, 1, 0,  1, 0,
		+.5, -.5, -.5,  1, 0, 0,  1, 1,
		-.5, +.5, -.5,  0, 1, 0,  0, 0,
	]);
	vertexBuffer.unmap();
	
	const uniformBufferSize = 16;
	const uniformBuffer = device.createBuffer({
		size: uniformBufferSize,
		usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST,
	});
	
	/* GPUTexture */
	// todo we could create a shortcut path for this, and save lots of copying :)
	const image = new Image();
	const imageLoadPromise = new Promise(resolve => {
		image.onload = () => resolve();
		image.src = "webkit-logo.png"
	});
	await Promise.resolve(imageLoadPromise);

	const texture = device.createTexture({
         size: {
			 width: image.width,
			 height: image.height,
			 depthOrArrayLayers: 1
         },
         arrayLayerCount: 1,
         mipLevelCount: 1,
         sampleCount: 1,
         dimension: "2d",
         format: preferredBackingFormat,
         usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST | GPUTextureUsage.RENDER_ATTACHMENT,
    });
	
	const imageBitmap = await createImageBitmap(image);

	device.queue.copyExternalImageToTexture( // todo glTexImage2D()
		{ source: imageBitmap },
		{ texture: texture },
		[image.width, image.height]
	);

	const sampler = device.createSampler({ // todo dep: just samplers, so can be cached
		magFilter: "linear",
		minFilter: "linear"
	});

	const vsShaderCode = `
				struct VertexIn {
				   @location(0) position: vec3<f32>,
				   @location(1) color: vec3<f32>,
				   @location(2) uv: vec2<f32>,
				};
	
				struct VertexOut {
				   @builtin(position) position: vec4<f32>,
				   @location(0) color: vec3<f32>,
				   @location(1) uv: vec2<f32>,
				};
				
				struct Uniforms {
					time : vec3<f32>,
					sx : f32,
				};
	
				@group(0) @binding(0) var<uniform> uniforms : Uniforms;
	
				@vertex
				fn main(@builtin(vertex_index) VertexIndex: u32, vin: VertexIn) -> VertexOut {
					let time = uniforms.time;
					let sx = uniforms.sx;
					let alpha = time[0];
					let beta = time[1];
					let gamma = time[2];
					let cA = cos(alpha);
					let sA = sin(alpha);
					let cB = cos(beta);
					let sB = sin(beta);
					let cG = cos(gamma);
					let sG = sin(gamma);
	
					let m = mat4x4(
						      cA * cB,          sA * cB,       -sB,   0,
						cA*sB*sG - sA*cG, sA*sB*sG + cA*cG,  cB * sG, 0,
						cA*sB*cG + sA*sG, sA*sB*cG - cA*sG,  cB * cG, 0,
						0, 0, 0, 1
					);
					
					let s = mat4x4(
						sx, 0, 0, 0,
						 0, 1, 0, 0,
						 0, 0, 1, 0,
						 0, 0, 0, 1
					);

					var vout : VertexOut;
					vout.position = s * (m * vec4<f32>(vin.position, 1));
					vout.position.z = (vout.position.z + 0.5) * 0.5;
					vout.color = vin.color;
					vout.uv = vin.uv;
					return vout;
				}
	`;

	const fsShaderCode = `
				struct VertexOut {
				   @builtin(position) position: vec4<f32>,
				   @location(0) color: vec3<f32>,
				   @location(1) uv: vec2<f32>,
				};
	
				@group(0) @binding(1) var colorTexture: texture_2d<f32>;
				@group(0) @binding(2) var textureSampler : sampler;

				@fragment
				fn main(in: VertexOut) -> @location(0) vec4<f32> {
					return max(vec4<f32>(in.color, 1) * 0.3, textureSample(colorTexture, textureSampler, in.uv));
				}
	`;

	const vsShader = device.createShaderModule({ code: vsShaderCode });
	const fsShader = device.createShaderModule({ code: fsShaderCode });
	
	/* GPUPipelineStageDescriptors */
	const vertexStageDescriptor = { // todo dep: [shader, attribute-bindings]
		module: vsShader,
		entryPoint: "main",
		buffers: [{
			arrayStride: vertexStride,
			stepMode: "vertex",
			attributes: [{
				format: "float32x4",
				offset: 0,
				shaderLocation: 0,
			}, {
				format: "float32x4",
				offset: 3 * 4,
				shaderLocation: 1,
			}, {
				format: "float32x2",
				offset: 6 * 4,
				shaderLocation: 2,
			}],
		}]
	};

	const fragmentStageDescriptor = { // todo dep: [shader, target formats]
		module: fsShader,
		entryPoint: "main",
		targets: [{format: preferredBackingFormat }],
	};

	/* GPURenderPipelineDescriptor */
	const renderPipelineDescriptor = { // todo dep: [shader, attribute-bindings, target formats, drawMode, cullMode]
		layout: "auto",
		vertex: vertexStageDescriptor,
		fragment: fragmentStageDescriptor,
		primitive: {
			topology: "triangle-list",
			cullMode: "back"
		},
	};
	
	/* GPURenderPipeline */
	const renderPipeline = device.createRenderPipeline(renderPipelineDescriptor);

	/*** Shader Setup ***/
	const uniformBindGroup = device.createBindGroup({ // todo dep: [uniform values, bound textures, used samplers, shader, attribute-bindings, target formats, drawMode, cullMode] (pretty much everything)
		layout: renderPipeline.getBindGroupLayout(0),
		entries: [
		  {
			binding: 0,
			resource: {
			  buffer: uniformBuffer,
			  offset: 0
			},
		  },
		  {
			binding: 1,
			resource: texture.createView(),
		  },
		  {
			binding: 2,
			resource: sampler,
		  },
		],
	  });

	
	/*** Swap Chain Setup ***/
	function frameUpdate() {
		const secondsBuffer = new Float32Array(4);
		const seconds = (new Date().getTime() * 0.001) % 60.0;
		const TAU = Math.PI * 2.0;
		secondsBuffer.set([seconds*10 * (TAU / 60.0),
						  seconds*5 * (TAU / 60.0),
						  seconds*1 * (TAU / 60.0),
						  innerHeight/innerWidth]);
		device.queue.writeBuffer(uniformBuffer, 0, secondsBuffer);

		const gpuContext = canvas.getContext("webgpu");
		
		/* GPUCanvasConfiguration */
		const canvasConfiguration = { device: device, format: preferredBackingFormat };
		gpuContext.configure(canvasConfiguration);
		
		/*** Render Pass Setup ***/
		/* Acquire Texture To Render To */
		/* GPUTexture */
		const currentTexture = gpuContext.getCurrentTexture();
		/* GPUTextureView */
		const renderAttachment = currentTexture.createView();
		
		
		/* GPURenderPassColorAttachmentDescriptor */
		const colorAttachmentDescriptor = { // todo dep: [target framebuffer, clearing]
			view: renderAttachment,
			loadOp: "clear",
			storeOp: "store",
			clearColor: {
				/* GPUColor */
				r: 0.15,
				g: 0.15,
				b: 0.5,
				a: 1
			}
		};
		
		/* GPURenderPassDescriptor */
		const renderPassDescriptor = { colorAttachments: [colorAttachmentDescriptor] }; // todo dep: [target framebuffer]
		
		/*** Rendering ***/
		
		/* GPUCommandEncoder */
		const commandEncoder = device.createCommandEncoder();
		/* GPURenderPassEncoder */
		const renderPassEncoder = commandEncoder.beginRenderPass(renderPassDescriptor);

		// todo this is a single draw call, dep: [everything]
		renderPassEncoder.setPipeline(renderPipeline);
		renderPassEncoder.setVertexBuffer(0, vertexBuffer);
		renderPassEncoder.setBindGroup(0, uniformBindGroup);
		renderPassEncoder.draw(36); // 36 vertices
		renderPassEncoder.end();
		
		/* GPUCommandBuffer */
		const commandBuffer = commandEncoder.finish();
		
		/* GPUQueue */
		const queue = device.queue;
		queue.submit([commandBuffer]);

		requestAnimationFrame(frameUpdate);
	}

	requestAnimationFrame(frameUpdate);
}

window.addEventListener("DOMContentLoaded", renderTexturedCube);
