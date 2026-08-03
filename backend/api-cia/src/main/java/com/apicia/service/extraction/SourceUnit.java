package com.apicia.service.extraction;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Path;

public class SourceUnit {
    public final Path path;
    public final CompilationUnit compilationUnit;

    public SourceUnit(Path path, CompilationUnit compilationUnit) {
        this.path = path;
        this.compilationUnit = compilationUnit;
    }
}
