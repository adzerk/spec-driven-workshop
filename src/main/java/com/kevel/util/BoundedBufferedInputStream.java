package com.kevel.util;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class BoundedBufferedInputStream extends BufferedInputStream {

    public final long allowableBytes;
    protected long boundedCount = 0;

    protected static final int EOF = -1;

    public BoundedBufferedInputStream(final InputStream in, final long allowableBytes) {
        super(in);
        this.allowableBytes = allowableBytes;
    }

    public BoundedBufferedInputStream(final InputStream in, final long allowableBytes, final int bufferSize) {
        super(in, bufferSize);
        this.allowableBytes = allowableBytes;
    }

    @Override
    public synchronized int read() throws IOException {
        if (boundedCount < allowableBytes) {
            final int res = super.read();
            ++boundedCount;
            return res;
        }
        return EOF;
    }

    @Override
    public synchronized int read(final byte[] b) throws IOException {
        return this.read(b, 0, b.length);
    }

    @Override
    public synchronized int read(final byte[] b, final int off, final int len) throws IOException {
        if (boundedCount < allowableBytes) {
            final long readAmount = Math.min(len, allowableBytes - boundedCount);
            final int res = super.read(b, off, (int) readAmount);
            if (res == EOF) {
                return EOF;
            }
            boundedCount += res;
            return res;
        }
        return EOF;
    }

    @Override
    public synchronized long skip(final long n) throws IOException {
        if (boundedCount < allowableBytes) {
            final long skipAmount = Math.min(n, allowableBytes - boundedCount);
            long ret = super.skip(skipAmount);
            boundedCount += ret;
            return ret;
        }
        return EOF;
    }

    public synchronized long skip(final long n, boolean countSkipped) throws IOException {
        if (countSkipped) {
            return this.skip(n);
        }
        return super.skip(n);
    }

    @Override
    public synchronized void skipNBytes(long n) throws IOException {
        if (boundedCount < allowableBytes) {
            final long skipAmount = Math.min(n, allowableBytes - boundedCount);
            super.skipNBytes(skipAmount);
            boundedCount += skipAmount;
        } else {
            throw new EOFException("Can't skip beyond boundary within bounded input stream");
        }
    }

    public synchronized void skipNBytes(long n, boolean countSkipped) throws IOException {
        if (countSkipped) {
            this.skipNBytes(n);
        } else {
            while (n > 0) {
                long skippedCount = super.skip(n);
                if (skippedCount > 0) {
                    n -= skippedCount;
                } else if (skippedCount == 0) {
                    // Confirm we're EOF
                    if (this.read() == EOF) {
                        throw new EOFException("Can't skip beyond boundary or EOF");
                    }
                } else {
                    throw new IOException("Unable to successfully skip");
                }
            }
        }
    }

    @Override
    public synchronized int available() throws IOException {
        if (boundedCount < allowableBytes) {
            return super.available();
        }
        return 0;
    }
}
