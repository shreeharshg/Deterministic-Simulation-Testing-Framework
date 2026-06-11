package com.sandbox.network;

// A Java Record automatically generates a constructor, getters, equals(), and hashCode()
public record Message(String from, String to, String payload) {}