package com.kevel.util;

final class DefaultExceptionHolder {
  private DefaultExceptionHolder() {}

  static final Exception DEFAULT_EXCEPTION = new SignalException("Error Result");

  static final class SignalException extends Exception {
    SignalException(String message) {
      super(message, null, false, false);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }
}
