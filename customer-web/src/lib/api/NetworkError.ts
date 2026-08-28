const NETWORK_ERROR_MESSAGE = 'The network request could not be completed.'

export class NetworkError extends Error {
  constructor(cause?: unknown) {
    super(NETWORK_ERROR_MESSAGE, { cause })
    this.name = 'NetworkError'
  }
}
