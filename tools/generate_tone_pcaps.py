#!/usr/bin/env python3
"""Utility to convert 8 kHz mono WAV files into RTP PCAP streams.

Usage example (run from SIPInspector/trunk):

    python tools/generate_tone_pcaps.py \
        --input-dir "C:/Users/SIP SUPPORT/Desktop/tones" \
        --output-dir pcap \
        --prefix "_8k"

Each WAV file is expected to match the pattern <name><prefix>.wav
(e.g. RBT_8k.wav) for the tone names listed with --tones. A matching
<name>_tone.pcap will be written to the output directory.

The script requires scapy (``pip install scapy``).
"""

import argparse
import os
import wave
import struct
import audioop

from scapy.all import RawPcapWriter  # type: ignore
from scapy.layers.inet import IP, UDP  # type: ignore
from scapy.packet import Raw  # type: ignore
from scapy.layers.l2 import Ether  # type: ignore

DEFAULT_TONES = [
    "RBT",
    "Hold",
    "Guide",
    "Conversation",
]


def pcm16_to_mulaw(sample: int) -> int:
    """Convert a signed 16-bit PCM sample to 8-bit mu-law (PCMU/PT=0)."""
    MAX = 0x7FFF
    BIAS = 0x84

    sample = max(-MAX, min(MAX, sample))
    sign = 0x80 if sample < 0 else 0
    if sign:
        sample = -sample
    sample += BIAS
    if sample > MAX:
        sample = MAX

    exponent = 7
    mask = 0x4000
    while exponent > 0 and not (sample & mask):
        mask >>= 1
        exponent -= 1

    mantissa = (sample >> (exponent + 3)) & 0x0F
    mu = ~(sign | (exponent << 4) | mantissa) & 0xFF
    return mu


def pcm16_to_alaw(sample: int) -> int:
    """Convert a signed 16-bit PCM sample to 8-bit A-law (PCMA/PT=8)."""
    MAX = 0x7FFF

    sample = max(-MAX, min(MAX, sample))
    sign = 0x00
    if sample < 0:
        sample = -sample
        sign = 0x80

    if sample >= 256:
        exponent = 7
        mask = 0x4000
        while exponent > 0 and not (sample & mask):
            mask >>= 1
            exponent -= 1
        mantissa = (sample >> (exponent + 3)) & 0x0F
        alaw = (exponent << 4) | mantissa
    else:
        alaw = (sample >> 4) & 0x1F

    alaw ^= (sign ^ 0x55)
    return alaw


class RtpPcapWriter:
    def __init__(self, output_path: str, payload_type: int = 8,
                 ssrc: int | None = None, frame_ms: int = 20,
                 sample_rate: int = 8000,
                 src_ip: str = "192.0.2.1",
                 dst_ip: str = "198.51.100.1",
                 src_port: int = 4000,
                 dst_port: int = 5000) -> None:
        self.writer = RawPcapWriter(output_path)
        self.payload_type = payload_type
        self.frame_ms = frame_ms
        self.sample_rate = sample_rate
        self.src_ip = src_ip
        self.dst_ip = dst_ip
        self.src_port = src_port
        self.dst_port = dst_port
        self.sequence = 0
        self.timestamp = 0
        if ssrc is None:
            ssrc = 0x12345678
        self.ssrc = ssrc

    def write_samples(self, samples: list[int]) -> None:
        samples_per_packet = int(self.sample_rate * self.frame_ms / 1000)
        if samples_per_packet <= 0:
            raise ValueError("Frame size too small; samples per packet <= 0")

        # Process samples in chunks of 160 samples (20ms at 8kHz)
        for i in range(0, len(samples), samples_per_packet):
            block = samples[i:i + samples_per_packet]
            if not block:
                continue

            # Convert 16-bit PCM to 8-bit G.711 samples one by one
            payload = bytearray()
            for sample in block:
                if self.payload_type == 0:  # PCMU
                    payload.append(audioop.lin2ulaw(struct.pack('<h', sample), 2)[0])
                elif self.payload_type == 8:  # PCMA
                    payload.append(audioop.lin2alaw(struct.pack('<h', sample), 2)[0])
                else:
                    raise ValueError(
                        f"Unsupported payload type {self.payload_type}; only 0 (PCMU) and 8 (PCMA) are supported")

            # Build RTP header with correct timestamp increment (160 for 20ms at 8kHz)
            rtp_header = self._build_rtp_header(len(block))
            
            # Create UDP packet with RTP payload
            udp_payload = rtp_header + bytes(payload)
            udp = UDP(sport=self.src_port, dport=self.dst_port, len=len(udp_payload) + 8)
            ip = IP(src=self.src_ip, dst=self.dst_ip)
            
            # Add Ethernet header for PCAP format
            ether = Ether(src="02:00:00:00:00:01", dst="02:00:00:00:00:02", type=0x0800)
            
            # Create and write the full packet
            packet = ether / ip / udp / Raw(udp_payload)
            self.writer.write(packet)
            
            # Update sequence number and timestamp for next packet
            self.sequence = (self.sequence + 1) & 0xFFFF
            self.timestamp = (self.timestamp + len(block)) & 0xFFFFFFFF

    def close(self) -> None:
        self.writer.close()

    def _build_rtp_header(self, sample_count: int) -> bytes:
        version = 2
        padding = 0
        extension = 0
        cc = 0
        marker = 0
        pt = self.payload_type & 0x7F
        vpxcc = (version << 6) | (padding << 5) | (extension << 4) | cc
        mpt = (marker << 7) | pt
        header = struct.pack(
            "!BBHII",
            vpxcc,
            mpt,
            self.sequence & 0xFFFF,
            self.timestamp & 0xFFFFFFFF,
            self.ssrc & 0xFFFFFFFF,
        )
        return header


def load_wav_samples(path: str) -> tuple[int, list[int]]:
    with wave.open(path, "rb") as wav_file:
        sample_rate = wav_file.getframerate()
        channels = wav_file.getnchannels()
        sampwidth = wav_file.getsampwidth()
        num_frames = wav_file.getnframes()
        frames = wav_file.readframes(num_frames)

    if channels != 1:
        raise ValueError(f"{path} must be mono; found {channels} channels")
    if sampwidth != 2:
        raise ValueError(f"{path} must be 16-bit PCM; found {sampwidth * 8}-bit")

    samples: list[int] = []
    for i in range(0, len(frames), 2):
        sample = int.from_bytes(frames[i:i + 2], byteorder="little", signed=True)
        samples.append(sample)
    return sample_rate, samples


def convert_wav_to_pcap(wav_path: str, pcap_path: str,
                        payload_type: int, frame_ms: int) -> None:
    sample_rate, samples = load_wav_samples(wav_path)
    if sample_rate != 8000:
        raise ValueError(f"{wav_path} must be 8000 Hz; found {sample_rate}")

    writer = RtpPcapWriter(pcap_path, payload_type=payload_type, frame_ms=frame_ms,
                           sample_rate=sample_rate)
    writer.write_samples(samples)
    writer.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert tone WAV files to RTP PCAPs")
    parser.add_argument("--input-dir", default="tones",
                        help="Directory containing <tone><prefix>.wav files (default: tones)")
    parser.add_argument("--output-dir", default="pcap",
                        help="Directory to write <tone>_tone.pcap files (default: pcap)")
    parser.add_argument("--tones", nargs="*", default=DEFAULT_TONES,
                        help="Tone base names to convert (default: RBT Hold Guide Conversation)")
    parser.add_argument("--prefix", default="_8k",
                        help="Suffix appended to tone name before .wav (default: _8k)")
    parser.add_argument("--frame-ms", type=int, default=20,
                        help="Milliseconds of audio per RTP packet (default: 20)")
    parser.add_argument("--payload-type", type=int, default=8,
                        help="RTP payload type (default: 8 for PCMA; use 0 for PCMU)")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    os.makedirs(args.output_dir, exist_ok=True)

    for tone in args.tones:
        wav_name = f"{tone}{args.prefix}.wav"
        wav_path = os.path.join(args.input_dir, wav_name)
        if not os.path.exists(wav_path):
            print(f"[SKIP] {wav_path} not found")
            continue
        pcap_name = f"{tone}_tone.pcap"
        pcap_path = os.path.join(args.output_dir, pcap_name)
        print(f"[INFO] Converting {wav_name} -> {pcap_name}")
        convert_wav_to_pcap(wav_path, pcap_path, args.payload_type, args.frame_ms)
    print("[DONE] Conversion complete")


if __name__ == "__main__":
    main()
