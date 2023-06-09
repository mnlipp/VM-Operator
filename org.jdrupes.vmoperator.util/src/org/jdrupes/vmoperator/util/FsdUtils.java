/*
 * VM-Operator
 * Copyright (C) 2023 Michael N. Lipp
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.jdrupes.vmoperator.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Utilities to access configurable file system directories. Based on
 * the [FHS](https://refspecs.linuxfoundation.org/FHS_3.0/fhs/index.html) and the
 * [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html).
 */
@SuppressWarnings("PMD.UseUtilityClass")
public class FsdUtils {

    /**
     * Adds a directory with the user's name to the path.
     * If such a directory does not exist yet, creates it.
     * If this file or the directory is not writable,
     * return the given path.
     *
     * @param path the path
     * @return the path
     */
    public static Path addUser(Path path) {
        String user = System.getProperty("user.name");
        if (user == null) {
            return path;
        }
        Path dir = path.resolve(user);
        if (Files.exists(dir)) {
            return dir;
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) { // NOPMD
            // Just trying, doesn't matter
        }
        if (!Files.isWritable(dir)) {
            return path;
        }
        return dir;
    }

    /**
     * Returns the directory for temporary storage.
     *
     * @return the path
     */
    public static Path tmpDir() {
        return Path.of(System.getProperty("java.io.tmpdir", "/tmp"));
    }

    /**
     * Returns the real home directory of the user or, if not
     * available, a sub directory in {@link #tmpDir()} with
     * the user's name. 
     *
     * @return the path
     */
    public static Path userHome() {
        Path home = Optional.ofNullable(System.getProperty("user.home"))
            .map(Path::of).orElse(null);
        if (home != null) {
            return home;
        }
        return addUser(tmpDir());
    }

    /**
     * Returns the data home.
     *
     * @param appName the application name
     * @return the path
     */
    public static Path dataHome(String appName) {
        return Optional.ofNullable(System.getenv().get("XDG_DATA_HOME"))
            .map(Path::of).orElse(userHome().resolve(".local").resolve("share"))
            .resolve(appName);
    }

    /**
     * Returns the config home.
     *
     * @param appName the application name
     * @return the path
     */
    public static Path configHome(String appName) {
        return Optional.ofNullable(System.getenv().get("XDG_CONFIG_HOME"))
            .map(Path::of).orElse(userHome().resolve(".config"))
            .resolve(appName);
    }

    /**
     * Returns the state directory.
     *
     * @param appName the application name
     * @return the path
     */
    public static Path stateHome(String appName) {
        return Optional.ofNullable(System.getenv().get("XDG_STATE_HOME"))
            .map(Path::of)
            .orElse(userHome().resolve(".local").resolve("state"))
            .resolve(appName);
    }

    /**
     * Returns the runtime directory.
     *
     * @param appName the application name
     * @return the path
     */
    public static Path runtimeDir(String appName) {
        return Optional.ofNullable(System.getenv("XDG_RUNTIME_DIR"))
            .map(Path::of).orElseGet(() -> {
                var runtimeBase = Path.of("/run");
                var dir = addUser(runtimeBase);
                if (!dir.equals(runtimeBase)) {
                    return dir;
                }
                return addUser(tmpDir());
            }).resolve(appName);
    }

    /**
     * Find a configuration file. The given filename is searched for in:
     * 
     * 1. the current working directory,
     * 1. the {@link #configHome(String)}
     * 1. the subdirectory `appName` of `/etc`
     *
     * @param appName the application name
     * @param filename the filename
     * @return the optional
     */
    public static Optional<Path> findConfigFile(String appName,
            String filename) {
        var candidates = List.of(Path.of(filename),
            configHome(appName).resolve(filename),
            Path.of("/etc").resolve(appName).resolve(filename));
        for (var candidate : candidates) {
            if (Files.exists(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

}
