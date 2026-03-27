package com.kevel.util;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface Result<T, E extends Throwable> extends Supplier<T>
    permits Result.Ok, Result.Err {

  sealed interface Ok<T, E extends Throwable> extends Result<T, E>
      permits Res.Ok, ContextualRes.Ok {
    T value();

    @Override
    default <R> R fold(
        Function<? super T, ? extends R> onOk, Function<? super Err<T, E>, ? extends R> onErr) {
      return onOk.apply(value());
    }
  }

  sealed interface Err<T, E extends Throwable> extends Result<T, E>
      permits Res.Err, ContextualRes.Err {
    Object context();

    E ex();

    @Override
    default <R> R fold(
        Function<? super T, ? extends R> onOk, Function<? super Err<T, E>, ? extends R> onErr) {
      return onErr.apply(this);
    }
  }

  <R> R fold(Function<? super T, ? extends R> onOk, Function<? super Err<T, E>, ? extends R> onErr);

  default <R> R foldError(
      Function<? super Err<T, E>, ? extends R> onErr, Function<? super T, ? extends R> onOk) {
    return fold(onOk, onErr);
  }

  @Override
  default T get() {
    return fold((value) -> value, (err) -> Result.sneakyThrow(err.ex()));
  }

  default T getChecked() throws E {
    return switch (this) {
      case Ok<T, E> ok -> ok.value();
      case Err<T, E> err -> Result.throwErr(err);
    };
  }

  default boolean isOk() {
    return fold((value) -> true, (err) -> false);
  }

  default boolean isErr() {
    return !isOk();
  }

  default Optional<T> toOptional() {
    return fold(Optional::ofNullable, (err) -> Optional.empty());
  }

  static <T, E extends Throwable> Res.Ok<T, E> ok(T value) {
    return Res.ok(value);
  }

  static <T> Res.Err<T, Exception> err(Object context) {
    return Res.err(context);
  }

  static <T> Res.Err<T, Exception> err(Object context, String message) {
    return Res.err(context, message);
  }

  static <T, E extends Throwable> Res.Err<T, E> err(Object context, E ex) {
    return Res.err(context, ex);
  }

  static <T, E extends Throwable, U, EE extends Throwable> Result<U, EE> pipe(
      Result<T, E> res, Function<T, Result<U, EE>> f1) {
    return f1.apply(res.get());
  }

  static <T, E extends Throwable, U, Uu, EE extends Throwable> Result<U, EE> pipe(
      Result<T, E> res, Function<T, Result<Uu, ?>> f1, Function<Uu, Result<U, EE>> f2) {
    return f2.apply(f1.apply(res.get()).get());
  }

  static <T, E extends Throwable, U, Uu, Uuu, EE extends Throwable> Result<U, EE> pipe(
      Result<T, E> res,
      Function<T, Result<Uu, ?>> f1,
      Function<Uu, Result<Uuu, ?>> f2,
      Function<Uuu, Result<U, EE>> f3) {
    return f3.apply(f2.apply(f1.apply(res.get()).get()).get());
  }

  @SuppressWarnings("unchecked")
  static <C> C context(Err<?, ?> err) {
    return (C) err.context();
  }

  static <T, E extends Throwable> T throwErr(Err<?, E> e) throws E {
    throw e.ex();
  }

  @SuppressWarnings("unchecked")
  private static <R, X extends Throwable> R sneakyThrow(Throwable throwable) throws X {
    throw (X) throwable;
  }
}
