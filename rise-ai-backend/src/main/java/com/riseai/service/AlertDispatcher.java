package com.riseai.service;

public interface AlertDispatcher {
    int dispatchDue();

    int purgeSent();
}
