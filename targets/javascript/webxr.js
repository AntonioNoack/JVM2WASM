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

    // todo use time to set engine time

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

	/*for (const view of pose.views) {
		const viewport = glLayer.getViewport(view);
		gl.viewport(viewport.x, viewport.y, viewport.width, viewport.height);
		drawScene(view.projectionMatrix, view.transform.inverse.matrix);
	}*/

    // prepare vr data
    webXR.views.length = numViews;
    webXR.viewports.length = numViews;
    for(let i=0;i<numViews;i++) {
        const view = pose.views[i];
        webXR.views[i] = view;
        webXR.viewports[i] = glLayer.getViewport(view);
    }

    // todo call engine loop in here

    // todo we need to draw our framebuffer onto glLayer.framebuffer somehow...
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

document.getElementById('enter-vr').addEventListener('click', () => {
	if (navigator.xr) {
		navigator.xr.requestSession('immersive-vr')
			.then(onSessionStarted)
			.catch(() => {
				console.error('WebXR-VR not supported');
				webXR.views.length = 0;
                webXR.viewports.length = 0;
                webXR.hasXRSession = false;
				// todo restart fallback loop somehow...
			});
	} else confirm('WebXR not supported')
});
