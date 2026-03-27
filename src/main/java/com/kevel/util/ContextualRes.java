package com.kevel.util;

import java.util.function.BiFunction;
import java.util.function.Function;

public sealed interface ContextualRes<T, C, E extends Throwable>
    permits ContextualRes.Ok, ContextualRes.Err {

  default <R> R foldContext(
      Function<? super T, ? extends R> onOk,
      BiFunction<? super C, ? super E, ? extends R> onErr) {
    return switch (this) {
      case Ok<T, C, E> o -> onOk.apply(o.value());
      case Err<T, C, E> e -> onErr.apply(e.context(), e.ex());
    };
  }

  default <U> U eitherContextual(Function<Ok<T, C, E>, U> oFn, Function<Err<T, C, E>, U> eFn) {
    return switch (this) {
      case Ok<T, C, E> o -> oFn.apply(o);
      case Err<T, C, E> e -> eFn.apply(e);
    };
  }

  default <U> ContextualRes<U, C, E> map(Function<? super T, ? extends U> mapper) {
    return foldContext(
        (value) -> ContextualRes.ok(mapper.apply(value)),
        (context, ex) -> ContextualRes.err(context, ex));
  }

  default <U> ContextualRes<U, C, E> flatMap(
      Function<? super T, ? extends ContextualRes<U, C, E>> mapper) {
    return foldContext(mapper::apply, ContextualRes::err);
  }

  default <C2> ContextualRes<T, C2, E> mapContext(Function<? super C, ? extends C2> mapper) {
    return foldContext(
        ContextualRes::ok,
        (context, ex) -> ContextualRes.err(mapper.apply(context), ex));
  }

  record Ok<U, C, E extends Throwable>(U value)
      implements ContextualRes<U, C, E>, Result.Ok<U, E> {}

  record Err<U, C, E extends Throwable>(C context, E ex)
      implements ContextualRes<U, C, E>, Result.Err<U, E> {}

  static <U, C, E extends Throwable> Ok<U, C, E> ok(U value) {
    return new Ok<>(value);
  }

  static <U, C, E extends Throwable> Err<U, C, E> err(C context, E ex) {
    return new Err<>(context, ex);
  }

  static <U, C> Err<U, C, Exception> err(C context) {
    return new Err<>(context, DefaultExceptionHolder.DEFAULT_EXCEPTION);
  }

  static <U, C> Err<U, C, Exception> err(C context, String message) {
    return new Err<>(context, new Exception(message));
  }

  @SuppressWarnings("unchecked")
  static <C> C context(Result.Err<?, ?> err) {
    return (C) err.context();
  }

  static <U, C, E extends Throwable> ContextualRes<U, C, E> fromResult(
      Result<U, E> result, Function<? super Object, ? extends C> contextMapper) {
    return result.fold(
        ContextualRes::ok,
        (err) -> ContextualRes.err(contextMapper.apply(err.context()), err.ex()));
  }
}
