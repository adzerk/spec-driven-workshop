package com.kevel.util;

import java.util.function.Function;

public sealed interface Res<T, E extends Throwable> permits Res.Ok, Res.Err {

  default <U> U either(Function<Ok<T, E>, U> oFn, Function<Err<T, E>, U> eFn) {
    return switch (this) {
      case Ok<T, E> o -> oFn.apply(o);
      case Err<T, E> e -> eFn.apply(e);
    };
  }

  default <U> Res<U, E> map(Function<? super T, ? extends U> mapper) {
    return switch (this) {
      case Ok<T, E> o -> Res.ok(mapper.apply(o.value()));
      case Err<T, E> e -> Res.err(e.context(), e.ex());
    };
  }

  default <U> Res<U, E> flatMap(Function<? super T, ? extends Res<U, E>> mapper) {
    return switch (this) {
      case Ok<T, E> o -> mapper.apply(o.value());
      case Err<T, E> e -> Res.err(e.context(), e.ex());
    };
  }

  default Res<T, E> mapErrorContext(Function<? super Object, ?> mapper) {
    return switch (this) {
      case Ok<T, E> o -> o;
      case Err<T, E> e -> Res.err(mapper.apply(e.context()), e.ex());
    };
  }

  default T recover(Function<? super Err<T, E>, ? extends T> recoverFn) {
    return switch (this) {
      case Ok<T, E> o -> o.value();
      case Err<T, E> e -> recoverFn.apply(e);
    };
  }

  record Ok<U, E extends Throwable>(U value) implements Res<U, E>, Result.Ok<U, E> {}

  record Err<U, E extends Throwable>(Object context, E ex) implements Res<U, E>, Result.Err<U, E> {}

  static <U, E extends Throwable> Ok<U, E> ok(U value) {
    return new Ok<>(value);
  }

  static <T> Err<T, Exception> err(Object context) {
    return new Err<>(context, DefaultExceptionHolder.DEFAULT_EXCEPTION);
  }

  static <T> Err<T, Exception> err(Object context, String message) {
    return new Err<>(context, new Exception(message));
  }

  static <T, E extends Throwable> Err<T, E> err(Object context, E ex) {
    return new Err<>(context, ex);
  }
}
