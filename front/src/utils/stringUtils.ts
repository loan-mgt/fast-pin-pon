/**
 * Truncates a string in the middle if it exceeds the length.
 * @param str The string to truncate
 * @param startChars Number of characters to keep at the start
 * @param endChars Number of characters to keep at the end
 * @param eclipse The string to use as the eclipse (default "...")
 */
export const truncateMiddle = (str: string, startChars: number, endChars: number, eclipse = '...'): string => {
    if (!str || str.length <= startChars + endChars + eclipse.length) {
        return str
    }
    return `${str.slice(0, startChars)}${eclipse}${str.slice(-endChars)}`
}
