package com.example.arnavigation.camera;

import static org.junit.Assert.assertEquals;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public final class CameraPreviewControllerTest {
    @Rule public final InstantTaskExecutorRule instantTaskExecutorRule =
            new InstantTaskExecutorRule();

    @Test
    public void permissionDenialNeverCallsCameraBackend() {
        FakeBackend backend = new FakeBackend();
        CameraPreviewController controller = new CameraPreviewController(() -> false, backend);
        List<CameraStatus> states = new ArrayList<>();

        controller.bindForTest(new TestOwner(), new Object(),
                state -> states.add(state.getStatus()));

        assertEquals(0, backend.bindCount);
        assertEquals(0, backend.unbindCount);
        assertEquals(CameraStatus.PERMISSION_DENIED, states.get(0));
    }

    @Test
    public void repeatedBindAndUnbindAreIdempotent() {
        FakeBackend backend = new FakeBackend();
        CameraPreviewController controller = new CameraPreviewController(() -> true, backend);
        TestOwner owner = new TestOwner();
        Object preview = new Object();
        List<CameraStatus> states = new ArrayList<>();

        controller.bindForTest(owner, preview, state -> states.add(state.getStatus()));
        backend.callbacks.get(0).onBound();
        controller.bindForTest(owner, preview, state -> states.add(state.getStatus()));
        controller.unbind();
        controller.unbind();

        assertEquals(1, backend.bindCount);
        assertEquals(1, backend.unbindCount);
        assertEquals(CameraStatus.UNBOUND, states.get(states.size() - 1));
    }

    @Test
    public void replacingFragmentSessionUnbindsOldSessionAndIgnoresLateResult() {
        FakeBackend backend = new FakeBackend();
        CameraPreviewController controller = new CameraPreviewController(() -> true, backend);
        List<CameraStatus> firstStates = new ArrayList<>();
        List<CameraStatus> secondStates = new ArrayList<>();

        controller.bindForTest(new TestOwner(), new Object(),
                state -> firstStates.add(state.getStatus()));
        controller.bindForTest(new TestOwner(), new Object(),
                state -> secondStates.add(state.getStatus()));
        backend.callbacks.get(0).onBound();
        backend.callbacks.get(1).onBound();

        assertEquals(2, backend.bindCount);
        assertEquals(1, backend.unbindCount);
        assertEquals(CameraStatus.UNBOUND, firstStates.get(firstStates.size() - 1));
        assertEquals(CameraStatus.BOUND, secondStates.get(secondStates.size() - 1));
    }

    @Test
    public void lifecycleDestroyReleasesCamera() {
        FakeBackend backend = new FakeBackend();
        CameraPreviewController controller = new CameraPreviewController(() -> true, backend);
        TestOwner owner = new TestOwner();

        controller.bindForTest(owner, new Object(), state -> {});
        backend.callbacks.get(0).onBound();
        owner.destroy();

        assertEquals(1, backend.unbindCount);
    }

    @Test
    public void backendFailureIsExposedAndSessionIsReleased() {
        FakeBackend backend = new FakeBackend();
        CameraPreviewController controller = new CameraPreviewController(() -> true, backend);
        List<CameraStatus> states = new ArrayList<>();

        controller.bindForTest(new TestOwner(), new Object(),
                state -> states.add(state.getStatus()));
        backend.callbacks.get(0).onFailure(CameraStatus.NO_BACK_CAMERA);

        assertEquals(1, backend.unbindCount);
        assertEquals(CameraStatus.NO_BACK_CAMERA, states.get(states.size() - 1));
    }

    private static final class FakeBackend implements CameraBindingBackend {
        int bindCount;
        int unbindCount;
        final List<Callback> callbacks = new ArrayList<>();

        @Override
        public void bind(LifecycleOwner owner, PreviewView previewView, Callback callback) {
            bindCount++;
            callbacks.add(callback);
        }

        @Override
        public void unbind() {
            unbindCount++;
        }
    }

    private static final class TestOwner implements LifecycleOwner {
        private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);

        TestOwner() {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        }

        @Override
        public Lifecycle getLifecycle() {
            return lifecycle;
        }

        void destroy() {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        }
    }
}
