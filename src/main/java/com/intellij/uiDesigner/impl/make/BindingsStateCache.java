/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.impl.make;

import consulo.index.io.KeyDescriptor;
import consulo.index.io.PersistentHashMap;
import consulo.index.io.data.DataExternalizer;
import consulo.index.io.data.IOUtil;
import consulo.util.io.FileUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public abstract class BindingsStateCache<T> {
    private static class PathKeyDescriptor implements KeyDescriptor<Path> {
        public static final PathKeyDescriptor INSTANCE = new PathKeyDescriptor();

        @Override
        public int hashCode(Path value) {
            return FileUtil.pathHashCode(value.toString());
        }

        @Override
        public boolean equals(Path val1, Path val2) {
            return FileUtil.pathsEqual(val1.toString(), val2.toString());
        }

        @Override
        public void save(DataOutput out, Path value) throws IOException {
            IOUtil.writeUTF(out, value.toString());
        }

        @Override
        public Path read(DataInput in) throws IOException {
            return Path.of(IOUtil.readUTF(in));
        }
    }

    private PersistentHashMap<Path, T> myMap;
    private final File myBaseFile;

    public BindingsStateCache(File storePath) throws IOException {
        myBaseFile = storePath;
        myMap = createMap(storePath);
    }

    protected abstract T read(DataInput stream) throws IOException;

    protected abstract void write(T t, DataOutput out) throws IOException;

    public void close() throws IOException {
        myMap.close();
    }

    public boolean wipe() {
        try {
            myMap.close();
        }
        catch (IOException ignored) {
        }
        PersistentHashMap.deleteFilesStartingWith(myBaseFile);
        try {
            myMap = createMap(myBaseFile);
        }
        catch (IOException ignored) {
            return false;
        }
        return true;
    }

    public void update(Path file, T state) throws IOException {
        if (state != null) {
            myMap.put(file, state);
        }
        else {
            myMap.remove(file);
        }
    }

    public T getState(Path file) throws IOException {
        return myMap.get(file);
    }

    private PersistentHashMap<Path, T> createMap(File file) throws IOException {
        return new PersistentHashMap<>(file, PathKeyDescriptor.INSTANCE, new DataExternalizer<T>() {
            @Override
            public void save(DataOutput out, T value) throws IOException {
                BindingsStateCache.this.write(value, out);
            }

            @Override
            public T read(DataInput in) throws IOException {
                return BindingsStateCache.this.read(in);
            }
        });
    }
}
