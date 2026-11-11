package ke.writ;

import net.minecraft.gametest.framework.GameTestHelper;

final class TestRestore {
    interface Action { void run() throws Exception; }

    static void finish(GameTestHelper helper, Action restore, Action verify, Throwable failure) {
        var server = helper.getLevel().getServer();
        try {
            restore.run();
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, reloadFailure) -> server.execute(() -> {
                Throwable result = failure;
                try {
                    if (reloadFailure != null) throw new IllegalStateException("restore reload failed", reloadFailure);
                    verify.run();
                } catch (Throwable e) {
                    if (result == null) result = e; else result.addSuppressed(e);
                }
                if (result == null) helper.succeed(); else helper.fail("test or restore failed: " + result);
            }));
        } catch (Throwable e) {
            if (failure != null) e.addSuppressed(failure);
            helper.fail("restore failed: " + e);
        }
    }
}
