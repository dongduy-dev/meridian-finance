package com.meridian.platform.shared.infrastructure.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseReadinessHealthIndicatorTest {
    @Test
    void queriesWithBoundedTimeoutAndClosesResources() throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(Statement.class); var result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(result); when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);
        assertEquals(Status.UP, new DatabaseReadinessHealthIndicator(source).health().getStatus());
        verify(statement).setQueryTimeout(2); verify(result).close(); verify(statement).close(); verify(connection).close();
    }

    @Test
    void downDoesNotIncludeRawDatabaseFailure() throws Exception {
        var source = mock(DataSource.class); when(source.getConnection()).thenThrow(new SQLException("private host/credential"));
        var health = new DatabaseReadinessHealthIndicator(source).health();
        assertEquals(Status.DOWN, health.getStatus()); assertTrue(health.getDetails().isEmpty());
    }
}
