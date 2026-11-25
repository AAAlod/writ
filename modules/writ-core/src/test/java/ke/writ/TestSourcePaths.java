package ke.writ;

import java.nio.file.Path;

final class TestSourcePaths {
    private TestSourcePaths() {}

    static Path projectFile(String path) {
        return Path.of(System.getProperty("writ.test.sourceRoot", ".")).resolve(path);
    }
}
