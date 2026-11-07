export enum LogLevel {
  DEBUG = 0,
  INFO = 1,
  WARN = 2,
  ERROR = 3,
  NONE = 4
}

interface LogEntry {
  level: LogLevel
  timestamp: string
  source: string
  message: string
  data?: unknown
}

class Logger {
  private source: string
  private level: LogLevel

  constructor(source: string) {
    this.source = source
    this.level = this.getLogLevelFromEnv()
  }

  private getLogLevelFromEnv(): LogLevel {
    if (import.meta.env.PROD) {
      return LogLevel.ERROR // Production: only errors
    }
    
    const envLevel = import.meta.env.VITE_LOG_LEVEL as string
    switch (envLevel?.toUpperCase()) {
      case 'DEBUG': return LogLevel.DEBUG
      case 'INFO': return LogLevel.INFO
      case 'WARN': return LogLevel.WARN
      case 'ERROR': return LogLevel.ERROR
      case 'NONE': return LogLevel.NONE
      default: return LogLevel.INFO // Development default
    }
  }

  private shouldLog(level: LogLevel): boolean {
    return level >= this.level
  }

  private createLogEntry(level: LogLevel, message: string, data?: unknown): LogEntry {
    return {
      level,
      timestamp: new Date().toISOString(),
      source: this.source,
      message,
      data
    }
  }

  private formatMessage(entry: LogEntry): string {
    const levelName = LogLevel[entry.level]
    return `[${entry.timestamp}] ${levelName} [${entry.source}] ${entry.message}`
  }

  private logToConsole(entry: LogEntry): void {
    const message = this.formatMessage(entry)
    
    switch (entry.level) {
      case LogLevel.DEBUG:
        console.debug(message, entry.data)
        break
      case LogLevel.INFO:
        console.info(message, entry.data)
        break
      case LogLevel.WARN:
        console.warn(message, entry.data)
        break
      case LogLevel.ERROR:
        console.error(message, entry.data)
        break
    }
  }

  private async logToRemote(entry: LogEntry): Promise<void> {
    if (!import.meta.env.PROD || entry.level < LogLevel.ERROR) {
      return // Only send errors to remote in production
    }

    try {
      // Send critical errors to monitoring service
      await fetch('/api/logs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(entry)
      })
    } catch (error) {
      // Fallback: log to console if remote logging fails
      console.error('Failed to send log to remote:', error)
    }
  }

  debug(message: string, data?: unknown): void {
    if (!this.shouldLog(LogLevel.DEBUG)) return
    
    const entry = this.createLogEntry(LogLevel.DEBUG, message, data)
    this.logToConsole(entry)
  }

  info(message: string, data?: unknown): void {
    if (!this.shouldLog(LogLevel.INFO)) return
    
    const entry = this.createLogEntry(LogLevel.INFO, message, data)
    this.logToConsole(entry)
  }

  warn(message: string, data?: unknown): void {
    if (!this.shouldLog(LogLevel.WARN)) return
    
    const entry = this.createLogEntry(LogLevel.WARN, message, data)
    this.logToConsole(entry)
    this.logToRemote(entry)
  }

  error(message: string, data?: unknown): void {
    if (!this.shouldLog(LogLevel.ERROR)) return
    
    const entry = this.createLogEntry(LogLevel.ERROR, message, data)
    this.logToConsole(entry)
    this.logToRemote(entry)
  }

  // Utility methods for common scenarios
  apiCall(method: string, url: string, status?: number): void {
    this.debug(`API ${method} ${url}${status ? ` - ${status}` : ''}`)
  }

  userAction(action: string, details?: unknown): void {
    this.info(`User action: ${action}`, details)
  }

  performance(operation: string, duration: number): void {
    if (duration > 1000) {
      this.warn(`Slow operation: ${operation} took ${duration}ms`)
    } else {
      this.debug(`Performance: ${operation} took ${duration}ms`)
    }
  }
}

export function createLogger(source: string): Logger {
  return new Logger(source)
}

// Global error handler for uncaught errors
if (typeof window !== 'undefined') {
  const globalLogger = createLogger('Global')
  
  window.addEventListener('error', (event) => {
    globalLogger.error('Uncaught error', {
      message: event.message,
      filename: event.filename,
      lineno: event.lineno,
      colno: event.colno,
      error: event.error
    })
  })

  window.addEventListener('unhandledrejection', (event) => {
    globalLogger.error('Unhandled promise rejection', {
      reason: event.reason
    })
  })
}