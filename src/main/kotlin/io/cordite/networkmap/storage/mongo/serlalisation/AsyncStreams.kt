package io.cordite.networkmap.storage.mongo.serlalisation

import com.mongodb.reactivestreams.client.Success
import com.mongodb.reactivestreams.client.gridfs.AsyncInputStream
import com.mongodb.reactivestreams.client.gridfs.AsyncOutputStream
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import org.reactivestreams.Publisher
import org.reactivestreams.Subscription
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

fun ByteBuf.asAsyncInputStream(): AsyncInputStream {
  return ByteBufAsyncInputStream(this)
}

fun ByteBuf.asAsyncOutputStream(): AsyncOutputStream {
  return ByteBufAsyncOutputStream(this)
}

class ByteBufAsyncInputStream(private val buffer: ByteBuf) : AsyncInputStream {
  override fun close(): Publisher<Success> {
    return Publisher {
      it.onNext(Success.SUCCESS)
      it.onComplete()
    }
  }

  override fun read(dst: ByteBuffer?): Publisher<Int> {
    var bytesWritten = 0
    return when (dst) {
      null -> Publisher { subscriber ->
        subscriber.onSubscribe(object : Subscription {
          override fun cancel() {
          }

          override fun request(n: Long) {
            subscriber.onNext(-1)
            subscriber.onComplete()
          }
        })
      }
      else -> Publisher { subscriber ->
        subscriber.onSubscribe(object : Subscription {
          override fun cancel() {
          }

          override fun request(n: Long) {
            try {
              val bytesToCopy = Math.min(dst.remaining(), buffer.writerIndex() - buffer.readerIndex())
              if (bytesToCopy > 0) {
                val tmp = PooledByteBufAllocator.DEFAULT.buffer(bytesToCopy)
                try {
                  buffer.readBytes(tmp, bytesToCopy)
                  dst.put(tmp.nioBuffer())
                } finally {
                  tmp.release()
                }
                bytesWritten += bytesToCopy
                subscriber.onNext(bytesToCopy)
              } else {
                subscriber.onNext(-1)
              }
              subscriber.onComplete()
            } catch (err: Throwable) {
              subscriber.onError(err)
            }
          }
        })
      }
    }
  }
}

class ByteBufAsyncOutputStream(private val buffer: ByteBuf) : AsyncOutputStream {
  override fun write(src: ByteBuffer?): Publisher<Int> {
    return when (src) {
      null -> Publisher { subscriber ->
        subscriber.onSubscribe(object : Subscription {
          override fun cancel() {
          }

          override fun request(n: Long) {
            subscriber.onNext(0)
            subscriber.onComplete()
          }
        })
      }
      else -> Publisher { subscriber ->
        subscriber.onSubscribe(object : Subscription {
          override fun cancel() {
          }

          override fun request(n: Long) {
            val size = src.remaining()
            buffer.writeBytes(src)
            subscriber.onNext(size)
            subscriber.onComplete()
          }
        })
      }
    }
  }

  override fun close(): Publisher<Success> {
    return Publisher { subscriber ->
      subscriber.onSubscribe(object : Subscription {
        override fun cancel() {
        }

        override fun request(n: Long) {
          subscriber.onNext(Success.SUCCESS)
          subscriber.onComplete()
        }
      })
    }
  }
}


fun OutputStream.toAsyncOutputStream() : AsyncOutputStream {
  @Suppress("DEPRECATION")
  return com.mongodb.reactivestreams.client.internal.GridFSAsyncStreamHelper.toAsyncOutputStream(
    com.mongodb.async.client.gridfs.helpers.AsyncStreamHelper.toAsyncOutputStream(this))
}

fun InputStream.toAsyncInputStream() : AsyncInputStream {
  @Suppress("DEPRECATION")
  return com.mongodb.reactivestreams.client.internal.GridFSAsyncStreamHelper.toAsyncInputStream(
    com.mongodb.async.client.gridfs.helpers.AsyncStreamHelper.toAsyncInputStream(this))
}