#!/bin/bash

# ============================================================================
# Vortex Authorization Service - Password Recovery Flow Test Runner
# ============================================================================
# Script for running password recovery tests with various options
# 
# Usage: ./run-auth-password-recovery-tests.sh [OPTIONS]
# 
# Options:
#   --unit              Run only unit tests
#   --integration       Run only integration tests  
#   --security          Run only security tests
#   --validation        Run only validation tests
#   --all               Run all tests (default)
#   --verbose           Enable verbose output
#   --quick             Quick mode - skip slow tests
#   --coverage          Generate test coverage report
#   --watch             Watch mode - rerun tests on file changes
#   --profile <env>     Run with specific profile (dev, test, prod)
#   --filter <pattern>  Filter tests by pattern
#   --report            Generate detailed HTML report
#   --ci                CI mode - optimized for continuous integration
#   --help              Show this help message
#
# Examples:
#   ./run-auth-password-recovery-tests.sh --unit --verbose
#   ./run-auth-password-recovery-tests.sh --security --coverage
#   ./run-auth-password-recovery-tests.sh --all --profile test
#   ./run-auth-password-recovery-tests.sh --filter "Recovery" --quick
# ============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# Default configuration
TEST_TYPE="all"
VERBOSE=false
QUICK_MODE=false
COVERAGE=false
WATCH_MODE=false
PROFILE="test"
FILTER=""
GENERATE_REPORT=false
CI_MODE=false
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
AUTH_SERVICE_DIR="$BASE_DIR/backend/vortex-authorization-service"
REPORT_DIR="$BASE_DIR/test-reports/auth-password-recovery"
COVERAGE_DIR="$BASE_DIR/coverage-reports/auth-password-recovery"

# Function to print colored output
print_color() {
    local color=$1
    shift
    echo -e "${color}$@${NC}"
}

# Function to print section headers
print_header() {
    echo ""
    print_color "$CYAN" "========================================"
    print_color "$CYAN" "$1"
    print_color "$CYAN" "========================================"
    echo ""
}

# Function to print test results
print_results() {
    local status=$1
    local test_name=$2
    if [ $status -eq 0 ]; then
        print_color "$GREEN" "✓ $test_name PASSED"
    else
        print_color "$RED" "✗ $test_name FAILED"
    fi
}

# Function to show help
show_help() {
    sed -n '/^# Usage:/,/^# ====/p' "$0" | grep -v '^# ====' | sed 's/^# //'
    exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --unit)
            TEST_TYPE="unit"
            shift
            ;;
        --integration)
            TEST_TYPE="integration"
            shift
            ;;
        --security)
            TEST_TYPE="security"
            shift
            ;;
        --validation)
            TEST_TYPE="validation"
            shift
            ;;
        --all)
            TEST_TYPE="all"
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --quick)
            QUICK_MODE=true
            shift
            ;;
        --coverage)
            COVERAGE=true
            shift
            ;;
        --watch)
            WATCH_MODE=true
            shift
            ;;
        --profile)
            PROFILE="$2"
            shift 2
            ;;
        --filter)
            FILTER="$2"
            shift 2
            ;;
        --report)
            GENERATE_REPORT=true
            shift
            ;;
        --ci)
            CI_MODE=true
            VERBOSE=false
            GENERATE_REPORT=true
            COVERAGE=true
            shift
            ;;
        --help)
            show_help
            ;;
        *)
            print_color "$RED" "Unknown option: $1"
            show_help
            ;;
    esac
done

# Check if authorization service directory exists
if [ ! -d "$AUTH_SERVICE_DIR" ]; then
    print_color "$RED" "Error: Authorization service directory not found: $AUTH_SERVICE_DIR"
    exit 1
fi

# Change to authorization service directory
cd "$AUTH_SERVICE_DIR"

# Create report directories if needed
if [ "$GENERATE_REPORT" = true ] || [ "$COVERAGE" = true ]; then
    mkdir -p "$REPORT_DIR"
    mkdir -p "$COVERAGE_DIR"
fi

# Build Maven test command
MVN_CMD="mvn"
MVN_ARGS=""

# Add verbose flag
if [ "$VERBOSE" = true ]; then
    MVN_ARGS="$MVN_ARGS -X"
fi

# Add profile
MVN_ARGS="$MVN_ARGS -Dquarkus.profile=$PROFILE"

# Add quick mode (skip slow tests)
if [ "$QUICK_MODE" = true ]; then
    MVN_ARGS="$MVN_ARGS -Dquarkus.test.exclude-tags=slow"
fi

# Add filter if specified
if [ -n "$FILTER" ]; then
    MVN_ARGS="$MVN_ARGS -Dtest=*${FILTER}*"
fi

# Add CI optimizations
if [ "$CI_MODE" = true ]; then
    MVN_ARGS="$MVN_ARGS -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
fi

# Function to run unit tests
run_unit_tests() {
    print_header "Running Unit Tests"
    
    local test_cmd="$MVN_CMD test $MVN_ARGS -Dtest=PasswordRecoveryServiceTest"
    
    if [ "$COVERAGE" = true ]; then
        test_cmd="$test_cmd jacoco:report"
    fi
    
    if [ "$VERBOSE" = true ]; then
        print_color "$YELLOW" "Command: $test_cmd"
    fi
    
    if $test_cmd; then
        print_results 0 "Unit Tests (15 tests)"
        return 0
    else
        print_results 1 "Unit Tests"
        return 1
    fi
}

# Function to run integration tests
run_integration_tests() {
    print_header "Running Integration Tests"
    
    local test_cmd="$MVN_CMD test $MVN_ARGS -Dtest=PasswordRecoveryResourceTest"
    
    if [ "$COVERAGE" = true ]; then
        test_cmd="$test_cmd jacoco:report"
    fi
    
    if [ "$VERBOSE" = true ]; then
        print_color "$YELLOW" "Command: $test_cmd"
    fi
    
    if $test_cmd; then
        print_results 0 "Integration Tests (15 tests)"
        return 0
    else
        print_results 1 "Integration Tests"
        return 1
    fi
}

# Function to run security tests
run_security_tests() {
    print_header "Running Security Tests"
    
    local test_cmd="$MVN_CMD test $MVN_ARGS -Dtest=PasswordRecoverySecurityTest"
    
    if [ "$COVERAGE" = true ]; then
        test_cmd="$test_cmd jacoco:report"
    fi
    
    if [ "$VERBOSE" = true ]; then
        print_color "$YELLOW" "Command: $test_cmd"
    fi
    
    if $test_cmd; then
        print_results 0 "Security Tests (8 tests)"
        return 0
    else
        print_results 1 "Security Tests"
        return 1
    fi
}

# Function to run validation tests
run_validation_tests() {
    print_header "Running Validation Tests"
    
    local test_cmd="$MVN_CMD test $MVN_ARGS -Dtest=PasswordRecoveryValidationTest"
    
    if [ "$COVERAGE" = true ]; then
        test_cmd="$test_cmd jacoco:report"
    fi
    
    if [ "$VERBOSE" = true ]; then
        print_color "$YELLOW" "Command: $test_cmd"
    fi
    
    if $test_cmd; then
        print_results 0 "Validation Tests (18 tests)"
        return 0
    else
        print_results 1 "Validation Tests"
        return 1
    fi
}

# Function to run all tests
run_all_tests() {
    print_header "Running All Password Recovery Tests"
    
    local test_cmd="$MVN_CMD test $MVN_ARGS -Dtest=PasswordRecoveryServiceTest,PasswordRecoveryResourceTest,PasswordRecoverySecurityTest,PasswordRecoveryValidationTest"
    
    if [ "$COVERAGE" = true ]; then
        test_cmd="$test_cmd jacoco:report"
    fi
    
    if [ "$VERBOSE" = true ]; then
        print_color "$YELLOW" "Command: $test_cmd"
    fi
    
    if $test_cmd; then
        print_results 0 "All Password Recovery Tests (56 tests)"
        return 0
    else
        print_results 1 "All Password Recovery Tests"
        return 1
    fi
}

# Function to generate test report
generate_test_report() {
    print_header "Generating Test Report"
    
    # Copy surefire reports
    if [ -d "target/surefire-reports" ]; then
        cp -r target/surefire-reports/* "$REPORT_DIR/" 2>/dev/null || true
        print_color "$GREEN" "Test reports copied to: $REPORT_DIR"
    fi
    
    # Generate summary
    local summary_file="$REPORT_DIR/summary.txt"
    echo "Password Recovery Test Execution Summary" > "$summary_file"
    echo "========================================" >> "$summary_file"
    echo "Date: $(date)" >> "$summary_file"
    echo "Profile: $PROFILE" >> "$summary_file"
    echo "Test Type: $TEST_TYPE" >> "$summary_file"
    
    if [ -f target/surefire-reports/TEST-*.xml ]; then
        local total_tests=$(grep -h 'tests=' target/surefire-reports/TEST-*.xml | sed 's/.*tests="\([0-9]*\)".*/\1/' | awk '{sum+=$1} END {print sum}')
        local failed_tests=$(grep -h 'failures=' target/surefire-reports/TEST-*.xml | sed 's/.*failures="\([0-9]*\)".*/\1/' | awk '{sum+=$1} END {print sum}')
        local errors=$(grep -h 'errors=' target/surefire-reports/TEST-*.xml | sed 's/.*errors="\([0-9]*\)".*/\1/' | awk '{sum+=$1} END {print sum}')
        
        echo "Total Tests: $total_tests" >> "$summary_file"
        echo "Failed: $failed_tests" >> "$summary_file"
        echo "Errors: $errors" >> "$summary_file"
        echo "Passed: $((total_tests - failed_tests - errors))" >> "$summary_file"
    fi
    
    print_color "$GREEN" "Summary saved to: $summary_file"
}

# Function to generate coverage report
generate_coverage_report() {
    print_header "Generating Coverage Report"
    
    if [ -d "target/site/jacoco" ]; then
        cp -r target/site/jacoco/* "$COVERAGE_DIR/" 2>/dev/null || true
        print_color "$GREEN" "Coverage report copied to: $COVERAGE_DIR"
        
        # Extract coverage percentage if available
        if [ -f "$COVERAGE_DIR/index.html" ]; then
            local coverage=$(grep -oP 'Total.*?(\d+)%' "$COVERAGE_DIR/index.html" | grep -oP '\d+' | head -1)
            if [ -n "$coverage" ]; then
                print_color "$MAGENTA" "Code Coverage: $coverage%"
            fi
        fi
    else
        print_color "$YELLOW" "No coverage report found. Make sure JaCoCo is configured."
    fi
}

# Function to run tests in watch mode
run_watch_mode() {
    print_header "Starting Watch Mode"
    print_color "$YELLOW" "Watching for file changes. Press Ctrl+C to stop."
    
    $MVN_CMD quarkus:test -Dquarkus.test.continuous-testing=enabled $MVN_ARGS
}

# Main execution
print_header "Vortex Password Recovery Test Runner"
print_color "$WHITE" "Configuration:"
print_color "$WHITE" "  Test Type: $TEST_TYPE"
print_color "$WHITE" "  Profile: $PROFILE"
print_color "$WHITE" "  Verbose: $VERBOSE"
print_color "$WHITE" "  Quick Mode: $QUICK_MODE"
print_color "$WHITE" "  Coverage: $COVERAGE"
print_color "$WHITE" "  CI Mode: $CI_MODE"

if [ -n "$FILTER" ]; then
    print_color "$WHITE" "  Filter: $FILTER"
fi

# Run in watch mode if requested
if [ "$WATCH_MODE" = true ]; then
    run_watch_mode
    exit $?
fi

# Run tests based on type
TEST_EXIT_CODE=0

case $TEST_TYPE in
    unit)
        run_unit_tests || TEST_EXIT_CODE=$?
        ;;
    integration)
        run_integration_tests || TEST_EXIT_CODE=$?
        ;;
    security)
        run_security_tests || TEST_EXIT_CODE=$?
        ;;
    validation)
        run_validation_tests || TEST_EXIT_CODE=$?
        ;;
    all)
        run_all_tests || TEST_EXIT_CODE=$?
        ;;
esac

# Generate reports if requested
if [ "$GENERATE_REPORT" = true ]; then
    generate_test_report
fi

if [ "$COVERAGE" = true ]; then
    generate_coverage_report
fi

# Print final summary
print_header "Test Execution Complete"

if [ $TEST_EXIT_CODE -eq 0 ]; then
    print_color "$GREEN" "✓ All tests passed successfully!"
else
    print_color "$RED" "✗ Some tests failed. Check the logs above for details."
fi

# Print report locations
if [ "$GENERATE_REPORT" = true ] || [ "$COVERAGE" = true ]; then
    echo ""
    print_color "$BLUE" "Reports generated:"
    if [ "$GENERATE_REPORT" = true ]; then
        print_color "$BLUE" "  Test Reports: $REPORT_DIR"
    fi
    if [ "$COVERAGE" = true ]; then
        print_color "$BLUE" "  Coverage Report: $COVERAGE_DIR"
    fi
fi

exit $TEST_EXIT_CODE