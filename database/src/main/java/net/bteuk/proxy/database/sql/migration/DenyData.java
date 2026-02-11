package net.bteuk.proxy.database.sql.migration;

public record DenyData(int id, String uuid, String reviewer, int bookId, int attempt, long denyTime) { }

