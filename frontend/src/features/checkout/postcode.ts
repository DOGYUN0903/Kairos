const SCRIPT_URL = "https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";

type PostcodeData = { zonecode: string; roadAddress: string; jibunAddress: string; address: string; userSelectedType?: string };
type DaumWindow = Window & { daum?: { Postcode: new (opts: { oncomplete: (d: PostcodeData) => void }) => { open: () => void } } };

let loading: Promise<void> | null = null;

function load(): Promise<void> {
  if ((window as DaumWindow).daum?.Postcode) return Promise.resolve();
  loading ??= new Promise<void>((resolve, reject) => {
    const s = document.createElement("script");
    s.src = SCRIPT_URL;
    s.async = true;
    s.onload = () => ((window as DaumWindow).daum?.Postcode ? resolve() : reject(new Error("우편번호 서비스를 찾지 못했습니다.")));
    s.onerror = () => reject(new Error("우편번호 스크립트를 불러오지 못했습니다."));
    document.head.appendChild(s);
  }).catch((e: unknown) => {
    // 다음 클릭에서 다시 시도할 수 있게 비우고, 실패한 태그도 치운다.
    loading = null;
    document.querySelector(`script[src="${SCRIPT_URL}"]`)?.remove();
    throw e;
  });
  return loading;
}

/** 버튼을 누를 때 처음 한 번만 스크립트를 불러 우편번호 창을 연다. 불러오기에 실패하면 reject. */
export async function openPostcode(onComplete: (r: { zipCode: string; address1: string }) => void): Promise<void> {
  await load();
  const Postcode = (window as DaumWindow).daum!.Postcode;
  new Postcode({
    oncomplete: (d) => onComplete({ zipCode: d.zonecode, address1: d.userSelectedType === "J" ? d.jibunAddress : d.roadAddress || d.address }),
  }).open();
}
