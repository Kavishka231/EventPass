import { useEffect, useRef, useState } from 'react';

import { Button, Alert } from '../ui';

interface BarcodeResult {
  rawValue: string;
}

interface BarcodeDetectorInstance {
  detect(source: ImageBitmapSource): Promise<BarcodeResult[]>;
}

interface BarcodeDetectorConstructor {
  new (options: { formats: string[] }): BarcodeDetectorInstance;
  getSupportedFormats?(): Promise<string[]>;
}

function detectorConstructor() {
  return (
    window as typeof window & { BarcodeDetector?: BarcodeDetectorConstructor }
  ).BarcodeDetector;
}

export function AdmissionScanner({
  disabled,
  onScan,
}: {
  disabled: boolean;
  onScan: (token: string) => void;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const frameRef = useRef<number | null>(null);
  const [active, setActive] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  function stop() {
    if (frameRef.current !== null) cancelAnimationFrame(frameRef.current);
    frameRef.current = null;
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setActive(false);
  }

  useEffect(() => stop, []);
  useEffect(() => {
    if (disabled) stop();
  }, [disabled]);

  async function start() {
    setMessage(null);
    const Detector = detectorConstructor();
    if (!window.isSecureContext && window.location.hostname !== 'localhost') {
      setMessage(
        'Camera scanning requires a secure HTTPS connection. Use manual entry instead.',
      );
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia || !Detector) {
      setMessage(
        'QR camera scanning is not supported by this browser. Use manual entry instead.',
      );
      return;
    }
    try {
      const formats = await Detector.getSupportedFormats?.();
      if (formats && !formats.includes('qr_code')) {
        setMessage(
          'This browser camera cannot detect QR codes. Use manual entry instead.',
        );
        return;
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
      streamRef.current = stream;
      const video = videoRef.current;
      if (!video) {
        stop();
        return;
      }
      video.srcObject = stream;
      await video.play();
      setActive(true);
      const detector = new Detector({ formats: ['qr_code'] });
      const scan = async () => {
        if (!streamRef.current || !videoRef.current) return;
        try {
          const [result] = await detector.detect(videoRef.current);
          if (result?.rawValue) {
            const token = result.rawValue.trim();
            stop();
            if (token) onScan(token);
            return;
          }
        } catch {
          setMessage(
            'The camera frame could not be read. Try again or use manual entry.',
          );
          stop();
          return;
        }
        frameRef.current = requestAnimationFrame(() => void scan());
      };
      frameRef.current = requestAnimationFrame(() => void scan());
    } catch (error) {
      const denied =
        error instanceof DOMException && error.name === 'NotAllowedError';
      setMessage(
        denied
          ? 'Camera permission was denied. Allow access in browser settings or use manual entry.'
          : 'No usable camera is available. Use manual entry instead.',
      );
      stop();
    }
  }

  return (
    <section className="admission-camera" aria-labelledby="camera-heading">
      <div>
        <p className="discovery-eyebrow">Camera</p>
        <h2 id="camera-heading">Scan ticket QR</h2>
        <p>
          The camera stops as soon as a code is captured or you leave this
          screen.
        </p>
      </div>
      <video
        ref={videoRef}
        className="admission-video"
        muted
        playsInline
        aria-label="Ticket QR camera preview"
        hidden={!active}
      />
      {message ? (
        <Alert tone="warning" title="Camera unavailable">
          {message}
        </Alert>
      ) : null}
      <Button
        variant={active ? 'outline' : 'secondary'}
        disabled={disabled}
        onClick={active ? stop : () => void start()}
      >
        {active ? 'Stop camera' : 'Start camera'}
      </Button>
    </section>
  );
}
