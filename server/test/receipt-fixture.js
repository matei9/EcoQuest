// Test-only deterministic OCR adapter: exercise upload plumbing without invoking an OCR process.
const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
export const fakeReceiptImage = (text, nonce) => Buffer.concat([signature, Buffer.from(JSON.stringify({ text, nonce }))]);
export const fakeReceiptOcr = async (image) => JSON.parse(image.subarray(8).toString()).text;
