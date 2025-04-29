window.webXR = {
    views: [],
    viewports: [],
    targetFramebuffer: null,
    hasXRSession: false,
};

let xrSession = null;
let glLayer = null;
let xrRefSpace = null;

function onXRFrame(time, frame) {

	const session = frame.session;
	session.requestAnimationFrame(onXRFrame);

	const pose = frame.getViewerPose(xrRefSpace);
	if (!pose) {
        // what are we supposed to do here?
        return;
    }

    webXR.targetFramebuffer = glLayer.framebuffer;

	gl.bindFramebuffer(gl.FRAMEBUFFER, glLayer.framebuffer);
	gl.clearColor(0.1,0.1,0.1,1.0);
	gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

    const numViews = pose.views.length;

    // prepare vr data
    webXR.views.length = numViews;
    webXR.viewports.length = numViews;
    for(let i=0;i<numViews;i++) {
        const view = pose.views[i];
        webXR.views[i] = view;
        webXR.viewports[i] = glLayer.getViewport(view);
    }

	// normal engine step; will call all necessary rendering steps
	const dt = (time - window.lastTime) / 1e3;
	safe(lib.EngineUpdate(window.innerWidth, window.innerHeight, dt));
	window.lastTime = time;
}

function onSessionStarted(session) {
	xrSession = session;
	gl.makeXRCompatible().then(() => {
		glLayer = new XRWebGLLayer(xrSession, gl);
		xrSession.updateRenderState({ baseLayer: glLayer });
		xrSession.requestReferenceSpace('local').then(refSpace => {
			xrRefSpace = refSpace;
			webXR.hasXRSession = true;
			xrSession.requestAnimationFrame(onXRFrame);
		});
	});
}

function startPseudoWebVR() {
	// run a pseudo webVR implementation for testing

	const n = 0.01, f = 1000;
	const projectionMatrix = [
		0.1, 0, 0, 0,
		0, 0.1, 0, 0,
		0, 0, -(f+n)/(f-n), -2*f*n/(f-n),
		0, 0, -1, 0
	];
	webXR.views = [
		{
			projectionMatrix,
			transform: {
				matrix: [
					1, 0, 0, 0,
					0, 1, 0, 0,
					0, 0, 1, 0,
					0, 0, 0, 1,
				]
			}
		}, {
			projectionMatrix,
			transform: {
				matrix: [
					1, 0, 0, 0,
					0, 1, 0, 0,
					0, 0, 1, 0,
					0, 0, 0, 1,
				]
			}
		}
	];
	webXR.viewports = [
		{ x: 0,   y: 0, width: 100, height: 100 },
		{ x: 100, y: 0, width: 100, height: 100 }
	];
	webXR.hasXRSession = true;

	const fb = gl.createFramebuffer();
	const tex = gl.createTexture();
	gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
	gl.bindTexture(gl.TEXTURE_2D, tex);
	gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 200, 100, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
	gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
	webXR.targetFramebuffer = fb;

	function render(time) {
		const dt = (time - window.lastTime) / 1e3;
		safe(lib.EngineUpdate(window.innerWidth, window.innerHeight, dt));
		window.lastTime = time;

		// blit result onto canvas
		gl.bindFramebuffer(gl.READ_FRAMEBUFFER,fb);
		gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,null); // null = canvas
		const my = window.innerHeight;
		gl.blitFramebuffer(0,0,200,100,0,my-100,200,my,gl.COLOR_BUFFER_BIT,gl.NEAREST);

		requestAnimationFrame(render);
	}
	requestAnimationFrame(render);
}

document.getElementById('enter-vr').addEventListener('click', () => {
	if (navigator.xr) {
		navigator.xr.requestSession('immersive-vr')
			.then(onSessionStarted)
			.catch(() => {
				console.error('WebXR-VR not supported');
				webXR.views.length = 0;
                webXR.viewports.length = 0;
                webXR.hasXRSession = false;
				// todo check that normal rendering loop is running, again
				startPseudoWebVR()
			});
	} else confirm('WebXR not supported')
});
