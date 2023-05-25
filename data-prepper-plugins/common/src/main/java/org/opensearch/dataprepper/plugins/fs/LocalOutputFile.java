/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.fs;

import org.apache.parquet.io.PositionOutputStream;
import org.opensearch.dataprepper.model.io.OutputFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class LocalOutputFile implements OutputFile {

    private static final long DEFAULT_BLOCK_SIZE = 8L * 1024L;

    private final File file;

    public LocalOutputFile(File file) {
        this.file = file;
    }

    @Override
    public PositionOutputStream create(long blockSizeHint) throws IOException {
        try {
            return LocalFilePositionOutputStream.create(file);
        } catch (FileNotFoundException e) {
            throw new IOException("Failed to create file: " + file.toString(), e);
        }
    }

    @Override
    public PositionOutputStream createOrOverwrite(long blockSizeHint) throws IOException {
        return create(blockSizeHint);
    }

    @Override
    public boolean supportsBlockSize() {
        return true;
    }

    @Override
    public long defaultBlockSize() {
        return DEFAULT_BLOCK_SIZE;
    }

}