/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.cohort.engine;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration of supported library formats and methods to convert from
 * common source identifiers to enum values/
 */
public 	enum LibraryFormat {
	CQL, XML;

	private static final Map<String,LibraryFormat> MIME_TYPE_TO_FORMAT = new HashMap<>();
	static {
		MIME_TYPE_TO_FORMAT.put("text/cql", CQL);
		MIME_TYPE_TO_FORMAT.put("application/cql", CQL);
		MIME_TYPE_TO_FORMAT.put("application/elm+xml", XML);		
	}
	
	private static final Map<String,LibraryFormat> EXTENSION_TO_FORMAT = new HashMap<>();
	static {
		EXTENSION_TO_FORMAT.put(".cql", CQL);
		EXTENSION_TO_FORMAT.put(".xml", XML);
	}
	
	public static LibraryFormat forPath(Path path) {
		return forString( path.toString() );
	}
	
	public static boolean isSupportedMimeType( String mimeType ) {
		return MIME_TYPE_TO_FORMAT.containsKey( mimeType );
	}
	
	public static boolean isSupportedExtension( String extension ) {
		return EXTENSION_TO_FORMAT.containsKey( extension );
	}
	
	public static boolean isSupportedPath( Path path ) {
		boolean isSupported = false;
		for( String key : EXTENSION_TO_FORMAT.keySet() ) {
			isSupported = path.getFileName().toString().endsWith( key );
			if( isSupported ) {
				break;
			}
		}
		return isSupported;
	}
	
	public static LibraryFormat forMimeType(String mimeType) { 
		LibraryFormat result = MIME_TYPE_TO_FORMAT.get( mimeType );	
		if( result == null ) {
			throw new IllegalArgumentException("Unsupported library format");
		}
		return result;
	}
	
	public static LibraryFormat forString(String pathString) {
		LibraryFormat result = null;
		if (pathString.matches("(?i).*xml")) {
			result = XML;
		} else if (pathString.matches("(?i).*cql")) {
			result = CQL;
		} else {
			throw new IllegalArgumentException(String.format("Unsupported file type \"%s\"", pathString));
		}
		return result;
	}
}
