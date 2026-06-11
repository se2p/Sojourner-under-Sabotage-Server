/**
 * @typedef TestDetails
 * @property {string} className
 * @property {number} elapsedTime
 * @property {number} startTime
 * @property {string} methodName
 * @property {string} testSuiteName
 * @property {string} accessDenied
 * @property {string} actualTestResult
 * @property {string} expectedTestResult
 * @property {string} testStatus
 * @property {string} trace
 */

/**
 * @typedef {Object} LogEntry
 * @property {number} orderIndex
 * @property {string} message
 * @property {string} methodName
 * @property {number} lineNumber
 * @property {string} testMethodName
 */

/**
 * @typedef {Object} TestResult
 * @property {string} testClassName
 * @property {'PASSED' | 'FAILED' | 'IGNORED'} testStatus
 * @property {number} elapsedTime
 * @property {Object<string, TestDetails>} testDetails
 * @property {Object<string, number>} coveredLines
 * @property {Object<string, number>} totalLines
 * @property {Object<string, Object<string, number>>} coverage
 * @property {Object<string, Object<number, Object<string, string>>>} variables
 * @property {Object<string, Array<LogEntry>>} logs
 * @property {string} message
 * @property {boolean} hiddenTestsPassed
 * @property {string} [hiddenTestsError]
 */

/**
 * @typedef {Object} SourceDTO
 * @property {string} cutComponentName
 * @property {string} className
 * @property {string} sourceCode
 * @property {Array<Range>} editable
 */

/**
 * @typedef {Object} ComponentData
 * @property {SourceDTO} test
 * @property {SourceDTO} cut
 * @property {TestResult} testResult
 *
 * @property {boolean} [nearlyActivatePopupShown]
 * @property {boolean} [activationPopupShown]
 */

/**
 * @typedef {Object} UserGameProgressionDTO
 * @property {number} id
 * @property {number} room
 * @property {string} componentName
 * @property {number} stage
 * @property {'DOOR'|'TALK'|'TEST'|'TESTS_ACTIVE'|'DESTROYED'|'MUTATED'|'DEBUGGING'} status
 */
